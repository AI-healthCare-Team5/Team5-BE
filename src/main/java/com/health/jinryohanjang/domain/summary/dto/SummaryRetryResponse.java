package com.health.jinryohanjang.domain.summary.dto;

import com.health.jinryohanjang.domain.summary.enums.SummaryStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record SummaryRetryResponse(
	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "요약지 ID (UUID). 다시 만들어도 그대로다.", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
	String summaryId,

	@Schema(description = "생성 상태. 항상 GENERATING", example = "GENERATING")
	SummaryStatus status
) {
}
