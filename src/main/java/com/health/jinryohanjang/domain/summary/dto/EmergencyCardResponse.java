package com.health.jinryohanjang.domain.summary.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.health.jinryohanjang.domain.guardian.enums.Relation;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;
import com.health.jinryohanjang.domain.summary.enums.AgeGroup;
import com.health.jinryohanjang.domain.summary.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응급 카드(E02) 응답이다. 못 물은 항목은 null("확인 못 했어요")이다.
 */
public record EmergencyCardResponse(
	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "응급 전환 시각", example = "2026-10-30T10:52:00+09:00")
	OffsetDateTime createdAt,

	@Schema(description = "위험 신호로 판정된 사용자 발화. AI 질문에 \"네\"로 답해 응급이 된 경우는 \"질문 → 네\" (예: \"가만히 있어도 숨이 차세요? → 네\")",
		example = "가슴이 조이고 식은땀이 나요")
	String userUtterance,

	@Schema(description = "위험 신호 종류", example = "CHEST_PAIN")
	RedFlagCategory redFlagCategory,

	@Schema(description = "주된 증상", example = "가슴이 조이고 식은땀이 남", nullable = true)
	String mainSymptom,

	@Schema(description = "시작 시점", example = "약 10분 전", nullable = true)
	String onsetDetail,

	@Schema(description = "나이대", example = "SEVENTIES", nullable = true)
	AgeGroup ageGroup,

	@Schema(description = "성별", example = "FEMALE", nullable = true)
	Gender gender,

	@Schema(description = "복용약. null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> medications,

	@Schema(description = "앓는 병. null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> conditions,

	@Schema(description = "알레르기. null = 확인 못 함", nullable = true)
	List<SummaryContent.HealthInfo> allergies,

	@Schema(description = "보호자. 로그인했고 등록된 경우에만 전체 번호로, 아니면 null", nullable = true)
	Guardian guardian
) {

	public record Guardian(
		@Schema(description = "보호자 관계", example = "DAUGHTER")
		Relation relation,

		@Schema(description = "전체 전화번호 (응급 카드에서만 가리지 않는다)", example = "010-1234-5678")
		String phoneNumber
	) {
	}
}
