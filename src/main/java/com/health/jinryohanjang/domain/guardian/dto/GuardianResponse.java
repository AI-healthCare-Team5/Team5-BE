package com.health.jinryohanjang.domain.guardian.dto;

import com.health.jinryohanjang.domain.guardian.enums.Relation;

import io.swagger.v3.oas.annotations.media.Schema;

public record GuardianResponse(
	@Schema(description = "보호자 ID", example = "4")
	Long guardianId,

	@Schema(description = "보호자 관계", example = "DAUGHTER")
	Relation relation,

	@Schema(description = "가운데를 가린 전화번호", example = "010-****-5678")
	String phoneNumber
) {
}
