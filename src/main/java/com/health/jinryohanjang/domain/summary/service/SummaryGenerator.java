package com.health.jinryohanjang.domain.summary.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

import com.health.jinryohanjang.common.util.BedrockChatClient.AiUnavailableException;
import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.enums.Stage;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Answered;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Option;
import com.health.jinryohanjang.domain.intake.service.BasicInfoQuestions;
import com.health.jinryohanjang.domain.intake.service.IntakeAi;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;
import com.health.jinryohanjang.domain.summary.enums.SummaryStep;
import com.health.jinryohanjang.domain.summary.model.Summary;
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 완료된 문진으로 요약지를 비동기로 만든다. BASIC_INFO → SYMPTOM → HEALTH_INFO → DEPARTMENT 순서로 진행 상태를 남긴다.
 * 시작 후 30초 안에 끝나지 않거나 AI 호출이 실패하면 FAILED로 바꾼다.
 * 저장할 때마다 Redis의 요약지가 그대로인지(취소·다시 만들기·마감 여부) 확인하고, 아니면 멈춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryGenerator {

	public static final Duration DEADLINE = Duration.ofSeconds(30);

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SummaryStore summaryStore;
	private final SummaryAi summaryAi;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * GENERATING 상태의 요약지를 바로 저장하고 생성을 시작한다. 응답 전에 저장하므로 곧바로 조회할 수 있다.
	 */
	public void start(IntakeSession session) {
		OffsetDateTime now = now();
		Summary summary = Summary.start(session.getSummaryId(), session.getIntakeId(), session.getGuestKey(), now,
			now.plus(DEADLINE));
		summaryStore.save(summary);
		submit(summary, session);
	}

	public void restart(Summary summary, IntakeSession session) {
		OffsetDateTime now = now();
		summary.restart(now, now.plus(DEADLINE));
		summaryStore.save(summary);
		submit(summary, session);
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdownNow();
	}

	private void submit(Summary summary, IntakeSession session) {
		executor.submit(() -> generate(summary, session));
	}

	private void generate(Summary summary, IntakeSession session) {
		try {
			summary.stepInProgress(SummaryStep.BASIC_INFO);
			if (!saveIfCurrent(summary)) {
				return;
			}
			fillBasicInfo(summary, session);
			summary.stepDone(SummaryStep.BASIC_INFO);
			summary.stepInProgress(SummaryStep.SYMPTOM);
			if (!saveIfCurrent(summary)) {
				return;
			}

			summary.fillItems(items(session));
			summary.stepDone(SummaryStep.SYMPTOM);
			summary.stepInProgress(SummaryStep.HEALTH_INFO);
			if (!saveIfCurrent(summary)) {
				return;
			}

			String medications = basicAnswer(session, BasicInfoQuestions.MEDICATIONS);
			String conditions = basicAnswer(session, BasicInfoQuestions.CONDITIONS);
			String allergies = basicAnswer(session, BasicInfoQuestions.ALLERGIES);
			SummaryAi.HealthInfoResult healthInfo = summaryAi.summarizeHealthInfo(medications, conditions, allergies);
			summary.fillHealthInfo(keepUserNumbers(healthInfo.medications(), medications),
				keepUserNumbers(healthInfo.conditions(), conditions), keepUserNumbers(healthInfo.allergies(), allergies));
			summary.stepDone(SummaryStep.HEALTH_INFO);
			summary.stepInProgress(SummaryStep.DEPARTMENT);
			if (!saveIfCurrent(summary)) {
				return;
			}

			summary.fillDepartment(new Summary.Department(session.getDepartmentCode(), session.getDepartmentName(),
				session.getRecommendReason()));
			summary.stepDone(SummaryStep.DEPARTMENT);
			summary.done();
			saveIfCurrent(summary);
		} catch (AiUnavailableException e) {
			log.warn("요약지 생성 중 AI 호출에 실패했습니다.", e);
			failIfCurrent(summary);
		} catch (RuntimeException e) {
			log.error("요약지 생성 실패", e);
			failIfCurrent(summary);
		}
	}

	private void fillBasicInfo(Summary summary, IntakeSession session) {
		AgeGroup ageGroup = AgeGroup.valueOf(basicAnswer(session, BasicInfoQuestions.AGE_GROUP));
		Gender gender = Gender.valueOf(basicAnswer(session, BasicInfoQuestions.GENDER));
		summary.fillBasicInfo(ageGroup, gender, session.getStartInputMode(), session.getMainSymptom(),
			session.getBodyPart(), session.getOnset(), session.getOnsetDetail());
	}

	/**
	 * 증상 문답을 항목으로 바꾼다. 네·아니요와 보기 답은 그대로 옮기고, 항목 이름과 자유 답 정리만 AI에 맡긴다.
	 */
	private List<Summary.Item> items(IntakeSession session) {
		List<Answered> answers = session.getAnswered().stream()
			.filter(answer -> answer.stage() == Stage.SYMPTOM)
			.toList();
		if (answers.isEmpty()) {
			return List.of();
		}
		List<SummaryAi.QuestionAnswer> questionAnswers = new ArrayList<>();
		for (int i = 0; i < answers.size(); i++) {
			Answered answer = answers.get(i);
			String text = answer.question().answerType() == AnswerType.FREE_TEXT
				? answer.rawAnswer() : readable(answer);
			questionAnswers.add(new SummaryAi.QuestionAnswer(i + 1, answer.question().text(), text));
		}
		Map<Integer, SummaryAi.ItemText> texts = summaryAi.summarizeItems(questionAnswers);

		List<Summary.Item> items = new ArrayList<>();
		for (int i = 0; i < answers.size(); i++) {
			Answered answer = answers.get(i);
			int order = i + 1;
			SummaryAi.ItemText text = texts.get(order);
			String label = text != null ? text.label() : answer.question().text();
			String value = answer.question().answerType() == AnswerType.FREE_TEXT
				? freeTextValue(answer, text) : readable(answer);
			items.add(new Summary.Item(order, label, answer.question().text(), value));
		}
		return items;
	}

	/**
	 * 자유 답은 AI가 정리한 값을 쓰되, 사용자 말에 없는 숫자가 생겼으면 ("사흘" → "3일") 사용자가 한 말을 그대로 쓴다.
	 */
	private String freeTextValue(Answered answer, SummaryAi.ItemText text) {
		if (text != null && text.value() != null && IntakeAi.keepsNumbers(text.value(), answer.rawAnswer())) {
			return text.value();
		}
		return IntakeAi.keepsNumbers(answer.normalizedAnswer(), answer.rawAnswer())
			? answer.normalizedAnswer() : answer.rawAnswer();
	}

	/**
	 * 건강정보의 자세한 내용에 사용자 말에 없는 숫자가 생겼으면 사용자가 한 말을 그대로 쓴다.
	 */
	private List<Summary.HealthInfo> keepUserNumbers(List<Summary.HealthInfo> values, String source) {
		if (values == null) {
			return null;
		}
		return values.stream()
			.map(value -> IntakeAi.keepsNumbers(value.detail(), source) ? value
				: new Summary.HealthInfo(value.name(), source))
			.toList();
	}

	private String readable(Answered answer) {
		String normalized = answer.normalizedAnswer();
		return switch (answer.question().answerType()) {
			case YES_NO_UNKNOWN -> switch (normalized) {
				case "YES" -> "있음";
				case "NO" -> "없음";
				default -> "잘 모르겠어요";
			};
			case SINGLE_CHOICE -> answer.question().options().stream()
				.filter(option -> option.code().equals(normalized))
				.map(Option::label)
				.findFirst()
				.orElse(normalized);
			case FREE_TEXT -> normalized;
		};
	}

	private String basicAnswer(IntakeSession session, String questionId) {
		return session.getAnswered().stream()
			.filter(answer -> answer.stage() == Stage.BASIC_INFO && answer.question().questionId().equals(questionId))
			.map(Answered::normalizedAnswer)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new IllegalStateException("기본 정보 답이 없습니다: " + questionId));
	}

	/**
	 * Redis의 요약지가 같은 generation이고 아직 GENERATING일 때만 저장한다. 마감이 지났으면 FAILED로 저장하고 멈춘다.
	 */
	private boolean saveIfCurrent(Summary summary) {
		if (!isCurrent(summary)) {
			return false;
		}
		if (now().isAfter(summary.getDeadline())) {
			summary.fail();
			summaryStore.save(summary);
			return false;
		}
		summaryStore.save(summary);
		return summary.isGenerating();
	}

	private void failIfCurrent(Summary summary) {
		if (isCurrent(summary)) {
			summary.fail();
			summaryStore.save(summary);
		}
	}

	private boolean isCurrent(Summary summary) {
		Optional<Summary> stored = summaryStore.find(summary.getSummaryId());
		return stored.isPresent() && stored.get().getGeneration() == summary.getGeneration()
			&& stored.get().isGenerating();
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(SEOUL).withNano(0);
	}
}
