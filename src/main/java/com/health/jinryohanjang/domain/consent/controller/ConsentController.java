package com.health.jinryohanjang.domain.consent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.auth.security.LoginMember;
import com.health.jinryohanjang.domain.consent.dto.ConsentRequest;
import com.health.jinryohanjang.domain.consent.dto.ConsentResponse;
import com.health.jinryohanjang.domain.consent.service.ConsentService;
import com.health.jinryohanjang.domain.consent.service.ConsentService.AgreeResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 기록 저장 동의 API를 제공한다.
 */
@Tag(name = "consent", description = "기록 저장 동의")
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

	private final ConsentService consentService;

	@Operation(summary = "기록 저장 동의 (C02)", description = """
		C02 "동의하고 시작하기". 예전 버전 동의가 있으면 withdrawn_at을 찍고 새 행을 저장한다. 동의 문구는 프론트 상수이다.

		| code | 설명 |
		| --- | --- |
		| `CONSENT_201` | 새로 동의함 |
		| `CONSENT_200` | 이미 같은 버전에 동의해 있음. 기존 동의를 그대로 반환 |
		| `CONSENT_400` | RECORD가 아닌 consentType, 현재 버전이 아닌 policyVersion (result.errors) |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""")
	@PostMapping
	public ResponseEntity<ApiResponse<ConsentResponse>> agree(
		@Parameter(hidden = true) @LoginMember Long memberId,
		@Valid @RequestBody ConsentRequest request) {
		AgreeResult result = consentService.agreeRecord(memberId, request);
		return ApiResponse.success(result.created() ? SuccessStatus.CONSENT_AGREED : SuccessStatus.CONSENT_ALREADY_AGREED,
			result.toResponse());
	}
}
