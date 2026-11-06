package com.health.jinryohanjang.domain.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SummaryConfirmResponse(
	@Schema(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
	String summaryId,

	@Schema(description = "\"내용이 맞아요\"를 눌렀는지. 항상 true", example = "true")
	boolean isConfirmed
) {
}
