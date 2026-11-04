package com.health.jinryohanjang.domain.hospital.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record HealthCenterResponse(
	@Schema(description = "마지막 공공데이터 동기화 시각", example = "2026-10-30T04:00:00+09:00")
	OffsetDateTime dataUpdatedAt,

	@Schema(description = "가장 가까운 보건소 1곳. 없으면 null", nullable = true)
	HospitalResponse hospital
) {
}
