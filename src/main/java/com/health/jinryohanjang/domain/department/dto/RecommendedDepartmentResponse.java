package com.health.jinryohanjang.domain.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecommendedDepartmentResponse(
	@Schema(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
	String intakeId,

	@Schema(description = "심평원 진료과목코드", example = "05")
	String departmentCode,

	@Schema(description = "진료과 이름", example = "정형외과")
	String departmentName,

	@Schema(description = "추천 이유", example = "무릎 통증이 사흘째이고 붓기가 있어 정형외과 진료를 권해요.")
	String recommendReason
) {
}
