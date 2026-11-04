package com.health.jinryohanjang.domain.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecordDeleteResponse(
	@Schema(description = "삭제한 진료 기록 ID", example = "12")
	Long medicalRecordId
) {
}
