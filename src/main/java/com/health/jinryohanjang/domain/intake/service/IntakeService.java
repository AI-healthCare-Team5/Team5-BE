package com.health.jinryohanjang.domain.intake.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.exception.GeneralExceptionAdvice.FieldErrorDetail;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.common.util.BedrockChatClient.AiUnavailableException;
import com.health.jinryohanjang.common.util.RateLimiter;
import com.health.jinryohanjang.domain.department.entity.Department;
import com.health.jinryohanjang.domain.department.repository.DepartmentRepository;
import com.health.jinryohanjang.domain.intake.dto.AnswerRequest;
import com.health.jinryohanjang.domain.intake.dto.IntakeCancelResponse;
import com.health.jinryohanjang.domain.intake.dto.IntakeResponse;
import com.health.jinryohanjang.domain.intake.dto.IntakeStartRequest;
import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;
import com.health.jinryohanjang.domain.intake.enums.Stage;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Question;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;

import lombok.RequiredArgsConstructor;

/**
 * 문진 시작·답변·이전 질문·취소를 처리한다.
 * 증상 질문(SYMPTOM, 최대 7개)은 AI가 만들고, 기본 정보(BASIC_INFO) 5개는 고정 질문이다.
 * 말·글 입력마다 위험 신호를 먼저 검사하고, 걸리면 바로 EMERGENCY로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Set<String> YES_NO_UNKNOWN = Set.of("YES", "NO", "UNKNOWN");
	private static final Set<String> NON_CLINICAL_DEPARTMENTS = Set.of("00", "16", "17", "18", "19", "20", "22", "25", "26");

	private final IntakeStore intakeStore;
	private final IntakeAi intakeAi;
	private final RedFlagDetector redFlagDetector;
	private final DepartmentRepository departmentRepository;
	private final RateLimiter rateLimiter;

	public StartResult start(IntakeStartRequest request, String guestKey, String clientIp) {
		rateLimiter.check(ErrorStatus.INTAKE_TOO_MANY_REQUESTS, "intake-start:ip:" + clientIp, 10, Duration.ofMinutes(1));
		validateStart(request);
		String ownerKey = StringUtils.hasText(guestKey) ? guestKey : UUID.randomUUID().toString();
		IntakeSession session = IntakeSession.start(UUID.randomUUID().toString(), ownerKey, request.inputMode(),
			trim(request.text()), OffsetDateTime.now(SEOUL).withNano(0));

		if (request.inputMode() == InputMode.BUTTON) {
			session.recordSymptomInfo(buttonMainSymptom(request), request.bodyPart(), request.onset(),
				request.onset().getLabel());
			session.replaceCollected(List.of(request.bodyPart().getLabel(), request.onset().getLabel()));
			Optional<RedFlagCategory> redFlag = redFlagDetector.detect(request.text());
			if (redFlag.isPresent()) {
				session.emergency(request.text(), redFlag.get());
			} else {
				session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(0));
			}
		} else {
			Optional<RedFlagCategory> redFlag = redFlagDetector.detect(request.text());
			if (redFlag.isPresent()) {
				session.recordSymptomInfo(trim(request.text()), null, null, null);
				session.emergency(request.text(), redFlag.get());
			} else {
				IntakeAi.StartResult result = callAi(() -> intakeAi.understandSymptom(request.text()));
				if (!result.understood()) {
					throw new GeneralException(ErrorStatus.INTAKE_NOT_UNDERSTOOD);
				}
				session.recordSymptomInfo(result.mainSymptom(), result.bodyPart(), result.onset(), result.onsetDetail());
				session.replaceCollected(result.collected());
				session.ask(Stage.SYMPTOM, result.firstQuestion());
			}
		}
		intakeStore.save(session);
		return new StartResult(toResponse(session), ownerKey);
	}

	public IntakeResponse get(String intakeId, String guestKey) {
		IntakeSession session = load(intakeId, guestKey);
		intakeStore.save(session);
		return toResponse(session);
	}

	public IntakeResponse answer(String intakeId, String guestKey, AnswerRequest request) {
		IntakeSession session = load(intakeId, guestKey);
		rateLimiter.check(ErrorStatus.INTAKE_TOO_MANY_REQUESTS, "intake-answer:guest:" + guestKey, 30,
			Duration.ofMinutes(1));
		if (!intakeStore.lock(intakeId)) {
			throw new GeneralException(ErrorStatus.INTAKE_DUPLICATE_ANSWER);
		}
		try {
			session = load(intakeId, guestKey);
			if (!session.isInProgress()) {
				throw alreadyFinished(session);
			}
			Question current = session.getCurrent();
			if (!current.questionId().equals(request.questionId())) {
				throw new GeneralException(ErrorStatus.INTAKE_DUPLICATE_ANSWER);
			}
			validateAnswerFormat(current, request);

			Optional<RedFlagCategory> redFlag = request.inputMode() == InputMode.BUTTON
				? Optional.empty() : redFlagDetector.detect(request.answer());
			if (redFlag.isPresent()) {
				session.emergency(request.answer(), redFlag.get());
			} else if (session.getStage() == Stage.SYMPTOM) {
				answerSymptom(session, request);
			} else {
				answerBasicInfo(session, request);
			}
			intakeStore.save(session);
			return toResponse(session);
		} finally {
			intakeStore.unlock(intakeId);
		}
	}

	public IntakeResponse back(String intakeId, String guestKey) {
		IntakeSession session = load(intakeId, guestKey);
		if (!session.isInProgress()) {
			throw alreadyFinished(session);
		}
		if (!session.back()) {
			throw new GeneralException(ErrorStatus.INTAKE_NO_PREVIOUS);
		}
		intakeStore.save(session);
		return toResponse(session);
	}

	public IntakeCancelResponse cancel(String intakeId, String guestKey) {
		IntakeSession session = load(intakeId, guestKey);
		intakeStore.delete(session);
		return new IntakeCancelResponse(intakeId, IntakeStatus.CANCELED);
	}

	/**
	 * 문진을 찾아 주인(guestKey)을 확인한다. guestKey가 없거나 다르면 403, 없으면 404다.
	 */
	public IntakeSession load(String intakeId, String guestKey) {
		if (!StringUtils.hasText(guestKey)) {
			throw new GeneralException(ErrorStatus.INTAKE_FORBIDDEN);
		}
		IntakeSession session = intakeStore.find(intakeId)
			.orElseThrow(() -> new GeneralException(ErrorStatus.INTAKE_NOT_FOUND));
		if (!session.isOwnedBy(guestKey)) {
			throw new GeneralException(ErrorStatus.INTAKE_FORBIDDEN);
		}
		return session;
	}

	private void answerSymptom(IntakeSession session, AnswerRequest request) {
		String buttonAnswer = request.inputMode() == InputMode.BUTTON ? request.answer() : null;
		IntakeAi.SymptomStep step = callAi(() -> intakeAi.nextSymptomStep(session, request.answer(), buttonAnswer));
		if (!step.understood()) {
			session.askAgain();
			return;
		}
		List<String> collected = step.collected().isEmpty() ? session.getCollected() : step.collected();
		session.answer(request.inputMode(), request.answer(), step.normalizedAnswer(), collected);
		if (step.done()) {
			session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(0));
		} else {
			session.ask(Stage.SYMPTOM, step.nextQuestion());
		}
	}

	private void answerBasicInfo(IntakeSession session, AnswerRequest request) {
		Question current = session.getCurrent();
		String normalized = current.answerType() == AnswerType.SINGLE_CHOICE
			? matchOption(current, request) : request.answer().trim();
		if (normalized == null) {
			session.askAgain();
			return;
		}
		List<String> collected = new ArrayList<>(session.getCollected());
		String label = BasicInfoQuestions.labelOf(current, normalized);
		if (label != null) {
			collected.add(label);
		}
		session.answer(request.inputMode(), request.answer(), normalized, collected);

		int answeredCount = (int)session.answeredCount(Stage.BASIC_INFO);
		if (answeredCount < IntakeSession.BASIC_INFO_QUESTIONS) {
			session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(answeredCount));
			return;
		}
		Map<String, String> candidates = new LinkedHashMap<>();
		for (Department department : departmentRepository.findAll()) {
			if (!NON_CLINICAL_DEPARTMENTS.contains(department.getCode())) {
				candidates.put(department.getCode(), department.getName());
			}
		}
		IntakeAi.DepartmentChoice choice = callAi(() -> intakeAi.recommendDepartment(session, candidates));
		session.complete(choice.departmentCode(), candidates.get(choice.departmentCode()), choice.recommendReason(),
			UUID.randomUUID().toString());
	}

	/**
	 * 버튼이면 보기 code 그대로, 말·글이면 code·보기 문구를 먼저 맞춰 보고 안 되면 AI로 맞춘다.
	 */
	private String matchOption(Question question, AnswerRequest request) {
		String answer = request.answer().trim();
		if (request.inputMode() == InputMode.BUTTON) {
			return answer;
		}
		for (IntakeSession.Option option : question.options()) {
			if (option.code().equalsIgnoreCase(answer) || answer.contains(option.label())) {
				return option.code();
			}
		}
		return callAi(() -> intakeAi.matchOption(question, answer));
	}

	private void validateStart(IntakeStartRequest request) {
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (request.inputMode() == InputMode.BUTTON) {
			if (request.bodyPart() == null) {
				errors.add(new FieldErrorDetail("bodyPart", "불편한 부위를 골라 주세요"));
			}
			if (request.onset() == null) {
				errors.add(new FieldErrorDetail("onset", "언제부터인지 골라 주세요"));
			}
		} else if (!StringUtils.hasText(request.text())) {
			errors.add(new FieldErrorDetail("text", "증상을 말하거나 적어 주세요"));
		}
		if (!errors.isEmpty()) {
			throw new GeneralException(ErrorStatus.INTAKE_INVALID_INPUT, Map.of("errors", errors));
		}
	}

	/**
	 * 버튼 답은 정해진 값이어야 하고, FREE_TEXT 질문은 버튼으로 답할 수 없다.
	 */
	private void validateAnswerFormat(Question question, AnswerRequest request) {
		if (request.inputMode() != InputMode.BUTTON) {
			return;
		}
		boolean valid = switch (question.answerType()) {
			case YES_NO_UNKNOWN -> YES_NO_UNKNOWN.contains(request.answer());
			case SINGLE_CHOICE -> question.options().stream().anyMatch(option -> option.code().equals(request.answer()));
			case FREE_TEXT -> false;
		};
		if (!valid) {
			throw new GeneralException(ErrorStatus.INTAKE_INVALID_ANSWER);
		}
	}

	private String buttonMainSymptom(IntakeStartRequest request) {
		String text = trim(request.text());
		return text != null ? text : request.bodyPart().getLabel() + " 불편";
	}

	private GeneralException alreadyFinished(IntakeSession session) {
		return new GeneralException(ErrorStatus.INTAKE_ALREADY_FINISHED, Map.of("status", session.getStatus()));
	}

	private <T> T callAi(AiCall<T> call) {
		try {
			return call.run();
		} catch (AiUnavailableException e) {
			throw new GeneralException(ErrorStatus.INTAKE_AI_UNAVAILABLE);
		}
	}

	private String trim(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	public IntakeResponse toResponse(IntakeSession session) {
		Question current = session.getCurrent();
		IntakeResponse.Question question = current == null ? null : new IntakeResponse.Question(
			current.questionId(), current.text(), current.answerType(),
			current.options() == null ? null : current.options().stream()
				.map(option -> new IntakeResponse.Option(option.code(), option.label()))
				.toList());
		IntakeResponse.Progress progress = new IntakeResponse.Progress(
			(int)session.answeredCount(session.getStage()) + (current == null ? 0 : 1),
			session.getStage() == Stage.SYMPTOM ? IntakeSession.SYMPTOM_MAX_QUESTIONS : IntakeSession.BASIC_INFO_QUESTIONS);
		IntakeResponse.Emergency emergency = session.getStatus() == IntakeStatus.EMERGENCY
			? new IntakeResponse.Emergency(session.getEmergencyUtterance(), session.getRedFlagCategory()) : null;
		IntakeResponse.Department department = session.getStatus() == IntakeStatus.COMPLETED
			? new IntakeResponse.Department(session.getDepartmentCode(), session.getDepartmentName(),
			session.getRecommendReason()) : null;
		return new IntakeResponse(session.getIntakeId(), session.getStatus(), session.getStage(), progress,
			session.getCollected(), question, session.isReask(), emergency, department, session.getSummaryId());
	}

	@FunctionalInterface
	private interface AiCall<T> {

		T run();
	}

	public record StartResult(IntakeResponse response, String guestKey) {
	}
}
