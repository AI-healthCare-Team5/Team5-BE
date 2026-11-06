package com.health.jinryohanjang.domain.intake.service;

import java.util.List;

import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Option;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Question;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

/**
 * 기본 정보(BASIC_INFO) 고정 질문 5개다. 나이대, 성별, 복용약, 앓는 병, 알레르기 순서다.
 */
public final class BasicInfoQuestions {

	public static final String AGE_GROUP = "q_age_group";
	public static final String GENDER = "q_gender";
	public static final String MEDICATIONS = "q_medications";
	public static final String CONDITIONS = "q_conditions";
	public static final String ALLERGIES = "q_allergies";

	public static final List<Question> ALL = List.of(
		new Question(AGE_GROUP, "연세가 어떻게 되세요?", AnswerType.SINGLE_CHOICE, List.of(
			new Option(AgeGroup.UNDER_50.name(), "50대 이하"),
			new Option(AgeGroup.SIXTIES.name(), "60대"),
			new Option(AgeGroup.SEVENTIES.name(), "70대"),
			new Option(AgeGroup.OVER_80.name(), "80대 이상"))),
		new Question(GENDER, "성별을 알려 주세요.", AnswerType.SINGLE_CHOICE, List.of(
			new Option(Gender.MALE.name(), "남성"),
			new Option(Gender.FEMALE.name(), "여성"))),
		new Question(MEDICATIONS, "지금 드시는 약이 있나요? 없으면 \"없어요\"라고 해 주세요.", AnswerType.FREE_TEXT, null),
		new Question(CONDITIONS, "앓고 계신 병이 있나요? 없으면 \"없어요\"라고 해 주세요.", AnswerType.FREE_TEXT, null),
		new Question(ALLERGIES, "약이나 음식 알레르기가 있나요? 없으면 \"없어요\"라고 해 주세요.", AnswerType.FREE_TEXT, null));

	private BasicInfoQuestions() {
	}

	public static Question get(int index) {
		return ALL.get(index);
	}

	public static String labelOf(Question question, String code) {
		if (question.options() == null) {
			return null;
		}
		return question.options().stream()
			.filter(option -> option.code().equals(code))
			.map(Option::label)
			.findFirst()
			.orElse(null);
	}
}
