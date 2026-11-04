package com.health.jinryohanjang.domain.consent.dto;

import java.time.OffsetDateTime;

import com.health.jinryohanjang.domain.consent.enums.ConsentType;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConsentResponse(
	@Schema(description = "동의 ID", example = "3")
	Long consentId,

	@Schema(description = "동의 종류", example = "RECORD")
	ConsentType consentType,

	@Schema(description = "동의한 문구 버전", example = "v1")
	String policyVersion,

	@Schema(description = "동의한 시각", example = "2026-10-30T14:00:00+09:00")
	OffsetDateTime agreedAt
) {
}
