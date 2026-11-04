package com.health.jinryohanjang.domain.summary.dto;

import java.util.List;

import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 요약지 수정 요청이다. 보낸 필드만 바꾸고, 안 보낸 필드는 그대로 둔다.
 * null을 보내면 "확인 못 함"(또는 지움)이다. ageGroup·gender·mainSymptom은 null로 바꿀 수 없다.
 */
public record SummaryUpdateRequest(
	@Schema(description = "나이대. null로 보낼 수 없음", example = "SEVENTIES", nullable = true)
	AgeGroup ageGroup,

	@Schema(description = "성별. null로 보낼 수 없음", example = "FEMALE", nullable = true)
	Gender gender,

	@Schema(description = "주된 증상 (최대 100자). null·빈 값 불가", example = "무릎 통증", nullable = true)
	@Size(max = 100, message = "100자까지 입력할 수 있어요")
	String mainSymptom,

	@Schema(description = "불편한 부위. null = 확인 못 함", example = "LEG_KNEE", nullable = true)
	BodyPart bodyPart,

	@Schema(description = "언제부터. onset만 보내면 onsetDetail은 onset 라벨(\"오늘\", \"2~3일 전부터\", \"일주일 이상\")로 바뀜",
		example = "DAYS_2_3", nullable = true)
	Onset onset,

	@Schema(description = "시작 시점 원문 (최대 50자)", example = "3일 전부터", nullable = true)
	@Size(max = 50, message = "50자까지 입력할 수 있어요")
	String onsetDetail,

	@Schema(description = "추가 내용. null = 지움", example = "계단 내려갈 때 더 아파요", nullable = true)
	String extraNote,

	@Schema(description = "복용약. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> medications,

	@Schema(description = "앓는 병. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> conditions,

	@Schema(description = "알레르기. 보내면 통째로 교체. [] = 없음, null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> allergies,

	@Schema(description = "증상별 항목. 보낸 order의 값만 수정", nullable = true)
	List<@Valid ItemUpdate> items
) {

	public record ItemUpdate(
		@Schema(description = "수정할 항목 순서", example = "1")
		int order,

		@Schema(description = "새 답. null = 확인 못 함", example = "있음", nullable = true)
		String value
	) {
	}
}
