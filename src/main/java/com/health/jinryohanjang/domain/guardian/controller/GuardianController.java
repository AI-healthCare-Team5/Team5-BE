package com.health.jinryohanjang.domain.guardian.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.guardian.dto.GuardianRequest;
import com.health.jinryohanjang.domain.guardian.dto.GuardianResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

/**
 * 보호자 연락처 API를 제공한다.
 */
@Tag(name = "guardian", description = "보호자 연락처")
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/members/me/guardian")
public class GuardianController {

	@Operation(summary = "보호자 등록·변경 (E03)", description = """
		보호자와 보호자 연락처 저장 동의(GUARDIAN)를 한 번에 저장한다. 회원당 보호자는 1명이고, 지워진 행이 있으면 되살려 덮어쓴다. 응답 전화번호는 가운데를 가린다.

		| code | 설명 |
		| --- | --- |
		| `GUARDIAN_200` | 등록·변경 성공 |
		| `GUARDIAN_400` | 동의 안 함(field consentAgreed), 전화번호 형식 오류(field phoneNumber), 없는 relation, 현재 버전이 아닌 policyVersion |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""")
	@PutMapping
	public ResponseEntity<ApiResponse<GuardianResponse>> upsert(@Valid @RequestBody GuardianRequest request) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}

	@Operation(summary = "보호자 삭제 (P01, E02)", description = """
		보호자에 deleted_at, 보호자 연락처 저장 동의에 withdrawn_at을 기록한다.

		| code | 설명 |
		| --- | --- |
		| `GUARDIAN_200` | 삭제 성공 (result null) |
		| `GUARDIAN_404` | 등록된 보호자가 없음 |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""")
	@DeleteMapping
	public ResponseEntity<ApiResponse<Void>> delete() {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}
}
