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
import java.util.regex.Pattern;

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
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;
import com.health.jinryohanjang.domain.summary.service.SummaryGenerator;

import lombok.RequiredArgsConstructor;

/**
 * 문진 시작·답변·이전 질문·취소를 처리한다.
 * 증상 질문(SYMPTOM, 최대 7개)은 AI가 만들고, 기본 정보(BASIC_INFO) 5개는 고정 질문이다.
 * 말·글 입력마다 위험 신호를 먼저 검사하고, 걸리면 바로 EMERGENCY로 바꾼다.
 * 문진이 COMPLETED 되면 요약지 생성을 바로 시작한다.
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Set<String> YES_NO_UNKNOWN = Set.of("YES", "NO", "UNKNOWN");
	private static final Pattern AFFIRMATIVE = Pattern.compile("^(네|예|응|맞아|맞습니다|그래요|그렇|있어요|있습니다)");
	private static final Set<String> NON_CLINICAL_DEPARTMENTS = Set.of("00", "16", "17", "18", "19", "20", "22", "25", "26");
	private static final String TOO_LONG_REASON = IntakeStartRequest.MAX_TEXT_LENGTH + "자까지 입력할 수 있어요";

	private final IntakeStore intakeStore;
	private final IntakeAi intakeAi;
	private final RedFlagDetector redFlagDetector;
	private final DepartmentRepository departmentRepository;
	private final RateLimiter rateLimiter;
	private final SummaryStore summaryStore;
	private final SummaryGenerator summaryGenerator;

	public StartResult start(IntakeStartRequest request, String guestKey, String clientIp) {
		rateLimiter.check(ErrorStatus.INTAKE_TOO_MANY_REQUESTS, "intake-start:ip:" + clientIp, 10, Duration.ofMinutes(1));
		validateStart(request);
		String ownerKey = StringUtils.hasText(guestKey) ? guestKey : UUID.randomUUID().toString();
		IntakeSession session = IntakeSession.start(UUID.randomUUID().toString(), ownerKey, request.inputMode(),
			trim(request.text()), now());

		if (request.inputMode() == InputMode.BUTTON) {
			session.recordSymptomInfo(buttonMainSymptom(request), request.bodyPart(), request.onset(),
				request.onset().getLabel());
			session.replaceCollected(List.of(request.bodyPart().getLabel(), request.onset().getLabel()));
			Optional<RedFlagCategory> redFlag = redFlagDetector.detect(request.text());
			if (redFlag.isPresent()) {
				session.emergency(request.text(), redFlag.get(), now());
			} else {
				session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(0));
			}
		} else {
			Optional<RedFlagCategory> redFlag = redFlagDetector.detect(request.text());
			if (redFlag.isPresent()) {
				session.recordSymptomInfo(trim(request.text()), null, null, null);
				session.emergency(request.text(), redFlag.get(), now());
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
		validateAnswerLength(request);
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

			RedFlagHit redFlag = detectRedFlag(session, current, request);
			if (redFlag != null) {
				session.emergency(redFlag.utterance(), redFlag.category(), now());
			} else if (session.getStage() == Stage.SYMPTOM) {
				answerSymptom(session, request);
			} else {
				answerBasicInfo(session, request);
			}
			intakeStore.save(session);
			if (session.getStatus() == IntakeStatus.COMPLETED) {
				summaryGenerator.start(session);
			}
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
		if (session.getSummaryId() != null) {
			summaryStore.delete(session.getSummaryId(), session.getGuestKey());
		}
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

	/**
	 * 못 알아들으면 같은 질문을 다시 묻는다. MAX_REASK번 다시 물어도 못 알아들으면 "잘 모르겠어요"로 기록하고 넘어간다.
	 * 그때도 AI가 다음 질문을 못 만들면 증상 질문을 끝내고 기본 정보로 간다.
	 */
	private void answerSymptom(IntakeSession session, AnswerRequest request) {
		String buttonAnswer = request.inputMode() == InputMode.BUTTON ? request.answer() : null;
		IntakeAi.SymptomStep step = callAi(() -> intakeAi.nextSymptomStep(session, request.answer(), buttonAnswer));
		if (!step.understood()) {
			if (!session.reaskLimitReached()) {
				session.askAgain();
				return;
			}
			String unknown = unknownAnswer(session.getCurrent());
			step = callAi(() -> intakeAi.nextSymptomStep(session, request.answer(), unknown));
			if (!step.understood()) {
				session.answer(request.inputMode(), request.answer(), unknown, session.getCollected());
				session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(0));
				return;
			}
		}
		List<String> collected = step.collected().isEmpty() ? session.getCollected() : step.collected();
		session.answer(request.inputMode(), request.answer(), step.normalizedAnswer(), collected);
		if (step.done()) {
			session.ask(Stage.BASIC_INFO, BasicInfoQuestions.get(0));
		} else {
			session.ask(Stage.SYMPTOM, step.nextQuestion());
		}
	}

	/**
	 * 답에서 위험 신호를 찾는다. 말·글은 답 자체를 검사한다.
	 * 증상 질문에 "네"(버튼 YES 또는 말·글의 "네")로 답하거나 보기 버튼을 고르면, AI가 물은 질문과 고른 보기를 합쳐 검사한다.
	 * AI에게는 위험 신호를 긍정형으로 묻게 하므로 "네"는 그 증상이 있다는 뜻이다.
	 */
	private RedFlagHit detectRedFlag(IntakeSession session, Question current, AnswerRequest request) {
		if (request.inputMode() != InputMode.BUTTON) {
			Optional<RedFlagCategory> inAnswer = redFlagDetector.detect(request.answer());
			if (inAnswer.isPresent()) {
				return new RedFlagHit(request.answer(), inAnswer.get());
			}
		}
		if (session.getStage() != Stage.SYMPTOM) {
			return null;
		}
		String chosen = switch (current.answerType()) {
			case YES_NO_UNKNOWN -> isYes(request) ? "네" : null;
			case SINGLE_CHOICE -> request.inputMode() == InputMode.BUTTON
				? BasicInfoQuestions.labelOf(current, request.answer()) : null;
			case FREE_TEXT -> null;
		};
		if (chosen == null) {
			return null;
		}
		String utterance = current.text() + " → " + chosen;
		return redFlagDetector.detect(current.text() + " " + chosen)
			.map(category -> new RedFlagHit(utterance, category))
			.orElse(null);
	}

	private boolean isYes(AnswerRequest request) {
		String answer = request.answer().trim();
		if (request.inputMode() == InputMode.BUTTON) {
			return "YES".equals(answer);
		}
		return AFFIRMATIVE.matcher(answer).find();
	}

	/**
	 * 다시 물어도 못 알아들은 답을 대신할 값이다. 네·아니요 질문은 UNKNOWN, 그 외에는 "잘 모르겠어요"다.
	 */
	private String unknownAnswer(Question question) {
		return question.answerType() == AnswerType.YES_NO_UNKNOWN ? "UNKNOWN" : IntakeAi.UNKNOWN_TEXT;
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
		if (tooLong(request.text())) {
			errors.add(new FieldErrorDetail("text", TOO_LONG_REASON));
		}
		if (!errors.isEmpty()) {
			throw new GeneralException(ErrorStatus.INTAKE_INVALID_INPUT, Map.of("errors", errors));
		}
	}

	/**
	 * 위험 신호 검사와 AI 호출 전에 답변 길이를 확인한다.
	 */
	private void validateAnswerLength(AnswerRequest request) {
		if (tooLong(request.answer())) {
			throw new GeneralException(ErrorStatus.INTAKE_INVALID_INPUT,
				Map.of("errors", List.of(new FieldErrorDetail("answer", TOO_LONG_REASON))));
		}
	}

	private boolean tooLong(String value) {
		return value != null && value.trim().length() > IntakeStartRequest.MAX_TEXT_LENGTH;
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

	private OffsetDateTime now() {
		return OffsetDateTime.now(SEOUL).withNano(0);
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

	private record RedFlagHit(String utterance, RedFlagCategory category) {
	}
}
