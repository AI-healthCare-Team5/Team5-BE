package com.health.jinryohanjang.domain.summary.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record SummarySaveResponse(
	@Schema(description = "보관한 진료 기록 ID", example = "12")
	Long medicalRecordId,

	@Schema(description = "보관한 시각", example = "2026-10-30T14:25:00+09:00")
	OffsetDateTime savedAt
) {
}
