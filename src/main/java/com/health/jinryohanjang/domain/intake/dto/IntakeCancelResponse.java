package com.health.jinryohanjang.domain.intake.dto;

import com.health.jinryohanjang.domain.intake.enums.IntakeStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record IntakeCancelResponse(
	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "문진 상태. 항상 CANCELED", example = "CANCELED")
	IntakeStatus status
) {
}
