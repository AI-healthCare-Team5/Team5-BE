package com.health.jinryohanjang.domain.department.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.department.dto.RecommendedDepartmentResponse;
import com.health.jinryohanjang.domain.intake.controller.IntakeController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Pattern;

/**
 * 추천 진료과 API를 제공한다.
 */
@Tag(name = "department", description = "진료과 추천")
@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
public class DepartmentController {

	@Operation(summary = "추천 진료과 조회 (S05)", description = """
		문진이 COMPLETED 될 때 한 번 계산해 저장한 진료과와 이유를 돌려준다. 여기서는 AI를 다시 부르지 않는다.

		| code | 설명 |
		| --- | --- |
		| `DEPARTMENT_200` | 조회 성공 |
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `DEPARTMENT_403` | guestKey가 없거나 이 문진의 것이 아님 → S01 |
		| `DEPARTMENT_404` | 문진이 없거나 보관 시간(1시간)이 지나 삭제됨 → S01 |
		| `DEPARTMENT_409` | 아직 완료(COMPLETED)되지 않은 문진 → 문진 화면으로 |
		""")
	@GetMapping("/api/intakes/{intakeId}/department")
	public ResponseEntity<ApiResponse<RecommendedDepartmentResponse>> getRecommendedDepartment(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = IntakeController.INVALID_ID_MESSAGE)
		String intakeId) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}
}
