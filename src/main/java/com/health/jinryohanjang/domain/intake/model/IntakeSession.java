package com.health.jinryohanjang.domain.intake.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;
import com.health.jinryohanjang.domain.intake.enums.Stage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis에 JSON으로 저장하는 문진 진행 상태다. 주인은 guestKey로 판정한다.
 * 이전 질문으로 돌아갈 수 있도록 답할 때마다 그때의 collected를 함께 남긴다.
 */
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
	getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeSession {

	public static final int SYMPTOM_MAX_QUESTIONS = 7;
	public static final int BASIC_INFO_QUESTIONS = 5;

	private String intakeId;
	private String guestKey;
	private IntakeStatus status;
	private Stage stage;
	private InputMode startInputMode;
	private OffsetDateTime createdAt;
	private String initialText;
	private String mainSymptom;
	private BodyPart bodyPart;
	private Onset onset;
	private String onsetDetail;
	private List<String> collected = new ArrayList<>();
	private List<Answered> answered = new ArrayList<>();
	private Question current;
	private boolean reask;
	private String emergencyUtterance;
	private RedFlagCategory redFlagCategory;
	private String departmentCode;
	private String departmentName;
	private String recommendReason;
	private String summaryId;

	public static IntakeSession start(String intakeId, String guestKey, InputMode inputMode, String initialText,
		OffsetDateTime createdAt) {
		IntakeSession session = new IntakeSession();
		session.intakeId = intakeId;
		session.guestKey = guestKey;
		session.status = IntakeStatus.IN_PROGRESS;
		session.stage = Stage.SYMPTOM;
		session.startInputMode = inputMode;
		session.initialText = initialText;
		session.createdAt = createdAt;
		return session;
	}

	public boolean isOwnedBy(String guestKey) {
		return this.guestKey != null && this.guestKey.equals(guestKey);
	}

	public boolean isInProgress() {
		return status == IntakeStatus.IN_PROGRESS;
	}

	public void recordSymptomInfo(String mainSymptom, BodyPart bodyPart, Onset onset, String onsetDetail) {
		this.mainSymptom = mainSymptom;
		this.bodyPart = bodyPart;
		this.onset = onset;
		this.onsetDetail = onsetDetail;
	}

	public void replaceCollected(List<String> collected) {
		this.collected = new ArrayList<>(collected);
	}

	public void ask(Stage stage, Question question) {
		this.stage = stage;
		this.current = question;
		this.reask = false;
	}

	public void askAgain() {
		this.reask = true;
	}

	/**
	 * 현재 질문에 대한 답을 남긴다. collected는 답하기 전 값을 함께 저장해서 이전 질문으로 돌아갈 때 되돌린다.
	 */
	public void answer(InputMode inputMode, String rawAnswer, String normalizedAnswer, List<String> collectedAfter) {
		answered.add(new Answered(stage, current, inputMode, rawAnswer, normalizedAnswer, List.copyOf(collected)));
		replaceCollected(collectedAfter);
	}

	/**
	 * 마지막 답을 지우고 그 질문을 다시 현재 질문으로 만든다. 돌아갈 질문이 없으면 false다.
	 */
	public boolean back() {
		if (answered.isEmpty()) {
			return false;
		}
		Answered last = answered.remove(answered.size() - 1);
		this.stage = last.stage();
		this.current = last.question();
		this.collected = new ArrayList<>(last.collectedBefore());
		this.reask = false;
		return true;
	}

	public void emergency(String utterance, RedFlagCategory category) {
		this.status = IntakeStatus.EMERGENCY;
		this.emergencyUtterance = utterance;
		this.redFlagCategory = category;
		this.current = null;
		this.reask = false;
	}

	public void complete(String departmentCode, String departmentName, String recommendReason, String summaryId) {
		this.status = IntakeStatus.COMPLETED;
		this.departmentCode = departmentCode;
		this.departmentName = departmentName;
		this.recommendReason = recommendReason;
		this.summaryId = summaryId;
		this.current = null;
		this.reask = false;
	}

	public long answeredCount(Stage stage) {
		return answered.stream().filter(answer -> answer.stage() == stage).count();
	}

	public record Question(String questionId, String text, AnswerType answerType, List<Option> options) {
	}

	public record Option(String code, String label) {
	}

	public record Answered(Stage stage, Question question, InputMode inputMode, String rawAnswer,
		String normalizedAnswer, List<String> collectedBefore) {
	}
}
