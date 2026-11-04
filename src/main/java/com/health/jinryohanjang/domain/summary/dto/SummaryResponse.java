package com.health.jinryohanjang.domain.summary.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.health.jinryohanjang.domain.summary.enums.StepStatus;
import com.health.jinryohanjang.domain.summary.enums.SummaryStatus;
import com.health.jinryohanjang.domain.summary.enums.SummaryStep;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요약지 조회와 수정이 함께 쓰는 응답이다.
 */
public record SummaryResponse(
	@Schema(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
	String summaryId,

	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "생성 상태. GENERATING이면 steps만, DONE이면 summary를 채운다.", example = "DONE")
	SummaryStatus status,

	@Schema(description = "S06a 4단계 진행 상황 (BASIC_INFO, SYMPTOM, HEALTH_INFO, DEPARTMENT 순서)")
	List<Step> steps,

	@Schema(description = "요약지 내용. DONE일 때만 채우고 그 외에는 null", nullable = true)
	SummaryContent summary,

	@Schema(description = "생성 시각", example = "2026-10-30T14:20:00+09:00")
	OffsetDateTime createdAt
) {

	public record Step(
		@Schema(description = "단계", example = "BASIC_INFO")
		SummaryStep step,

		@Schema(description = "단계 진행 상태", example = "DONE")
		StepStatus status
	) {
	}
}
