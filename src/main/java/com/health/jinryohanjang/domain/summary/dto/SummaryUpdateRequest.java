package com.health.jinryohanjang.domain.summary.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * 요약지 수정 요청이다. 보낸 필드만 바꾸고, 안 보낸 필드는 그대로 둔다.
 * null을 보내면 "확인 못 함"(또는 지움)이다. ageGroup·gender·mainSymptom은 null로 바꿀 수 없다.
 * 안 보낸 필드와 null로 보낸 필드를 구분해야 해서 record 대신 setter가 불린 필드를 기록한다.
 * 경로 변수 검증과 함께 쓰면 Bean Validation 오류의 field가 "request"로 나가서, 값 검증은 서비스에서 한다.
 */
@Getter
public class SummaryUpdateRequest {

	public static final String AGE_GROUP = "ageGroup";
	public static final String GENDER = "gender";
	public static final String MAIN_SYMPTOM = "mainSymptom";
	public static final String BODY_PART = "bodyPart";
	public static final String ONSET = "onset";
	public static final String ONSET_DETAIL = "onsetDetail";
	public static final String EXTRA_NOTE = "extraNote";
	public static final String MEDICATIONS = "medications";
	public static final String CONDITIONS = "conditions";
	public static final String ALLERGIES = "allergies";
	public static final String ITEMS = "items";

	public static final int MAIN_SYMPTOM_MAX_LENGTH = 100;
	public static final int ONSET_DETAIL_MAX_LENGTH = 50;

	@Schema(description = "나이대. null로 보낼 수 없음", example = "SEVENTIES", nullable = true)
	private AgeGroup ageGroup;

	@Schema(description = "성별. null로 보낼 수 없음", example = "FEMALE", nullable = true)
	private Gender gender;

	@Schema(description = "주된 증상 (최대 100자). null·빈 값 불가", example = "무릎 통증", nullable = true,
		maxLength = MAIN_SYMPTOM_MAX_LENGTH)
	private String mainSymptom;

	@Schema(description = "불편한 부위. null = 확인 못 함", example = "LEG_KNEE", nullable = true)
	private BodyPart bodyPart;

	@Schema(description = "언제부터. onset만 보내면 onsetDetail은 onset 라벨(\"오늘\", \"2~3일 전부터\", \"일주일 이상\")로 바뀜",
		example = "DAYS_2_3", nullable = true)
	private Onset onset;

	@Schema(description = "시작 시점 원문 (최대 50자)", example = "3일 전부터", nullable = true,
		maxLength = ONSET_DETAIL_MAX_LENGTH)
	private String onsetDetail;

	@Schema(description = "추가 내용. null = 지움", example = "계단 내려갈 때 더 아파요", nullable = true)
	private String extraNote;

	@Schema(description = "복용약. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	private List<SummaryContent.HealthInfo> medications;

	@Schema(description = "앓는 병. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	private List<SummaryContent.HealthInfo> conditions;

	@Schema(description = "알레르기. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	private List<SummaryContent.HealthInfo> allergies;

	@Schema(description = "증상별 항목. 보낸 order의 값만 수정", nullable = true)
	private List<ItemUpdate> items;

	@JsonIgnore
	@Getter(AccessLevel.NONE)
	private final Set<String> sent = new HashSet<>();

	/**
	 * 요청 본문에 이 필드가 있었는지 (null로 보낸 경우도 포함).
	 */
	public boolean has(String field) {
		return sent.contains(field);
	}

	public void setAgeGroup(AgeGroup ageGroup) {
		this.ageGroup = ageGroup;
		sent.add(AGE_GROUP);
	}

	public void setGender(Gender gender) {
		this.gender = gender;
		sent.add(GENDER);
	}

	public void setMainSymptom(String mainSymptom) {
		this.mainSymptom = mainSymptom;
		sent.add(MAIN_SYMPTOM);
	}

	public void setBodyPart(BodyPart bodyPart) {
		this.bodyPart = bodyPart;
		sent.add(BODY_PART);
	}

	public void setOnset(Onset onset) {
		this.onset = onset;
		sent.add(ONSET);
	}

	public void setOnsetDetail(String onsetDetail) {
		this.onsetDetail = onsetDetail;
		sent.add(ONSET_DETAIL);
	}

	public void setExtraNote(String extraNote) {
		this.extraNote = extraNote;
		sent.add(EXTRA_NOTE);
	}

	public void setMedications(List<SummaryContent.HealthInfo> medications) {
		this.medications = medications;
		sent.add(MEDICATIONS);
	}

	public void setConditions(List<SummaryContent.HealthInfo> conditions) {
		this.conditions = conditions;
		sent.add(CONDITIONS);
	}

	public void setAllergies(List<SummaryContent.HealthInfo> allergies) {
		this.allergies = allergies;
		sent.add(ALLERGIES);
	}

	public void setItems(List<ItemUpdate> items) {
		this.items = items;
		sent.add(ITEMS);
	}

	public record ItemUpdate(
		@Schema(description = "수정할 항목 순서", example = "1")
		Integer order,

		@Schema(description = "새 답. null = 확인 못 함", example = "있음", nullable = true)
		String value
	) {
	}
}
