package com.health.jinryohanjang.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.auth.security.AuthCookies;
import com.health.jinryohanjang.domain.auth.security.LoginMember;
import com.health.jinryohanjang.domain.member.dto.MemberInfoResponse;
import com.health.jinryohanjang.domain.member.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/**
 * 내 정보와 회원 탈퇴 API를 제공한다.
 */
@Tag(name = "member", description = "내 정보, 탈퇴")
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final AuthCookies authCookies;

	@Operation(summary = "내 정보 조회 (P01)", description = """
		계정, 동의 상태, 기록 수, 보호자를 돌려준다. 보호자가 없으면 guardian은 null이고, 전화번호는 가운데를 가린다.

		| code | 설명 |
		| --- | --- |
		| `MEMBER_200` | 조회 성공 |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""")
	@GetMapping
	public ResponseEntity<ApiResponse<MemberInfoResponse>> getMyInfo(
		@Parameter(hidden = true) @LoginMember Long memberId) {
		return ApiResponse.success(SuccessStatus.MEMBER_GET_SUCCESS, memberService.getMyInfo(memberId));
	}

	@Operation(summary = "회원 탈퇴 (P01)", description = """
		회원은 status = WITHDRAWN과 deleted_at, 기록·보호자는 deleted_at, 동의는 withdrawn_at을 기록한다. refreshToken과 모든 쿠키를 지우고, 남은 accessToken도 바로 막는다. 이 기기(guestKey)의 문진·요약지도 지운다.

		30일 안에 같은 카카오 계정으로 다시 오면 회원 행만 복구하고, 30일이 지나면 배치가 완전히 지운다.

		| code | 설명 |
		| --- | --- |
		| `MEMBER_200` | 탈퇴 성공 (result null) |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""")
	@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
	@DeleteMapping
	public ResponseEntity<ApiResponse<Void>> withdraw(
		@Parameter(hidden = true) @LoginMember Long memberId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey) {
		memberService.withdraw(memberId, guestKey);
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.ACCESS_TOKEN).toString());
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.REFRESH_TOKEN).toString());
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.GUEST_KEY).toString());
		return ApiResponse.success(SuccessStatus.MEMBER_WITHDRAW_SUCCESS, null, headers);
	}
}
