package com.health.jinryohanjang.domain.summary.dto;

import java.util.List;

import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요약지 조회·수정과 기록 상세가 함께 쓰는 요약지 내용이다.
 * 못 물은 항목은 null("확인 못 했어요"), 물었는데 없다고 하면 [] ("없음")이다.
 */
public record SummaryContent(
	@Schema(description = "나이대", example = "SEVENTIES")
	AgeGroup ageGroup,

	@Schema(description = "성별", example = "FEMALE")
	Gender gender,

	@Schema(description = "문진을 시작한 방식", example = "VOICE")
	InputMode inputMode,

	@Schema(description = "주된 증상", example = "무릎 통증")
	String mainSymptom,

	@Schema(description = "불편한 부위. null = 확인 못 함", example = "LEG_KNEE", nullable = true)
	BodyPart bodyPart,

	@Schema(description = "언제부터. null = 확인 못 함", example = "DAYS_2_3", nullable = true)
	Onset onset,

	@Schema(description = "시작 시점 원문", example = "3일 전부터", nullable = true)
	String onsetDetail,

	@Schema(description = "추가 내용", example = "계단 내려갈 때 더 아파요", nullable = true)
	String extraNote,

	@Schema(description = "복용약. [] = 없음, null = 확인 못 함", nullable = true)
	List<HealthInfo> medications,

	@Schema(description = "앓는 병. [] = 없음, null = 확인 못 함", nullable = true)
	List<HealthInfo> conditions,

	@Schema(description = "알레르기. [] = 없음, null = 확인 못 함", nullable = true)
	List<HealthInfo> allergies,

	@Schema(description = "증상별 항목")
	List<Item> items,

	@Schema(description = "추천 진료과")
	Department department,

	@Schema(description = "\"내용이 맞아요\"를 눌렀는지", example = "false")
	boolean isConfirmed
) {

	public record HealthInfo(
		@Schema(description = "이름", example = "혈압약")
		String name,

		@Schema(description = "자세한 내용", example = "매일 아침", nullable = true)
		String detail
	) {
	}

	public record Item(
		@Schema(description = "순서. 수정할 때 이 값으로 항목을 고른다.", example = "1")
		int order,

		@Schema(description = "항목 이름", example = "붓기")
		String label,

		@Schema(description = "물어본 질문", example = "무릎이 부었나요?")
		String question,

		@Schema(description = "답. null = 확인 못 함", example = "있음", nullable = true)
		String value
	) {
	}

	public record Department(
		@Schema(description = "심평원 진료과목코드", example = "05")
		String departmentCode,

		@Schema(description = "진료과 이름", example = "정형외과")
		String departmentName,

		@Schema(description = "추천 이유", example = "무릎 통증이 사흘째이고 붓기가 있어 정형외과 진료를 권해요.")
		String recommendReason
	) {
	}
}
