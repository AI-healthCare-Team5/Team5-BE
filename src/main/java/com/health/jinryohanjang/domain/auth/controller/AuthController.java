package com.health.jinryohanjang.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.auth.dto.AuthorizeUrlResponse;
import com.health.jinryohanjang.domain.auth.dto.KakaoLoginRequest;
import com.health.jinryohanjang.domain.auth.dto.KakaoLoginResponse;
import com.health.jinryohanjang.domain.auth.security.AuthCookies;
import com.health.jinryohanjang.domain.auth.security.AuthTokenStore;
import com.health.jinryohanjang.domain.auth.security.JwtProvider;
import com.health.jinryohanjang.domain.auth.service.AuthService;
import com.health.jinryohanjang.domain.auth.service.AuthService.AuthorizeUrl;
import com.health.jinryohanjang.domain.auth.service.AuthService.LoginResult;
import com.health.jinryohanjang.domain.auth.service.AuthService.Tokens;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 카카오 로그인과 토큰 API를 제공한다.
 */
@Tag(name = "auth", description = "카카오 로그인, 토큰")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final AuthCookies authCookies;
	private final JwtProvider jwtProvider;
	private final AuthTokenStore authTokenStore;

	@Operation(summary = "카카오 로그인 URL (L01)", description = """
		카카오 인가 URL을 돌려주고 `oauthState` 쿠키(HttpOnly, 10분)를 발급한다. URL에 같은 state가 들어 있다.

		| code | 설명 |
		| --- | --- |
		| `AUTH_200` | 조회 성공. 프론트는 authorizeUrl로 이동 |
		| `AUTH_400` | 허용 목록에 없는 redirectUri |
		""")
	@GetMapping("/kakao/authorize-url")
	public ResponseEntity<ApiResponse<AuthorizeUrlResponse>> getAuthorizeUrl(
		@Parameter(description = "카카오가 돌려보낼 프론트 콜백 주소. 서버 허용 목록(로컬·배포)에 있어야 한다.",
			example = "https://jinryohanjang.pages.dev/auth/kakao/callback")
		@RequestParam String redirectUri) {
		AuthorizeUrl authorizeUrl = authService.createAuthorizeUrl(redirectUri);
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE,
			authCookies.create(AuthCookies.OAUTH_STATE, authorizeUrl.state(), AuthCookies.OAUTH_STATE_TTL).toString());
		return ApiResponse.success(SuccessStatus.AUTHORIZE_URL_SUCCESS,
			new AuthorizeUrlResponse(authorizeUrl.url()), headers);
	}

	@Operation(summary = "카카오 로그인 (L01, L01a)", description = """
		카카오 code로 로그인하고 `accessToken`(30분)·`refreshToken`(14일) 쿠키를 발급한다. `oauthState` 쿠키는 삭제하고, `guestKey` 쿠키는 그대로 둔다.

		처음이면 회원을 만들고, 탈퇴 후 30일 안이면 회원 행만 복구한다. needsRecordConsent가 true면 C02로 보낸다. C02는 "로그인하고 보관하기"로 들어왔을 때만 보내고, E03·P01에서 로그인했으면 원래 화면으로 돌아간다.

		| code | 설명 |
		| --- | --- |
		| `AUTH_200` | 로그인 성공 |
		| `AUTH_400` | code 없음·만료·재사용, state 불일치·만료, 허용 안 된 redirectUri → L01a |
		| `AUTH_502` | 카카오 서버 응답 실패 → L01a |
		""")
	@SecurityRequirement(name = SwaggerConfig.OAUTH_STATE)
	@PostMapping("/kakao/login")
	public ResponseEntity<ApiResponse<KakaoLoginResponse>> login(
		@Valid @RequestBody KakaoLoginRequest request,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.OAUTH_STATE, required = false) String savedState) {
		LoginResult result = authService.login(request.code(), request.state(), savedState, request.redirectUri());
		HttpHeaders headers = tokenCookies(result.tokens());
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.OAUTH_STATE).toString());
		return ApiResponse.success(SuccessStatus.LOGIN_SUCCESS, result.response(), headers);
	}

	@Operation(summary = "토큰 재발급", description = """
		`AUTH_401`을 받으면 호출하고, 성공하면 원래 요청을 다시 보낸다. accessToken·refreshToken 쿠키를 새로 발급한다 (refreshToken은 매번 교체).

		| code | 설명 |
		| --- | --- |
		| `AUTH_200` | 재발급 성공 (result null) |
		| `AUTH_401` | refreshToken 없음·만료·폐기, 탈퇴한 회원 → 비로그인 상태로 (필요하면 L01) |
		""")
	@SecurityRequirement(name = SwaggerConfig.REFRESH_TOKEN)
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<Void>> refresh(
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		Tokens tokens = authService.refresh(refreshToken);
		return ApiResponse.success(SuccessStatus.REFRESH_SUCCESS, null, tokenCookies(tokens));
	}

	@Operation(summary = "로그아웃 (P01)", description = """
		토큰 상태와 상관없이 항상 성공한다. Redis의 refreshToken과 guestKey의 문진·요약지를 지우고, accessToken·refreshToken·guestKey 쿠키를 삭제한다.

		| code | 설명 |
		| --- | --- |
		| `AUTH_200` | 로그아웃 성공 (result null) |
		""")
	@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
	@SecurityRequirement(name = SwaggerConfig.REFRESH_TOKEN)
	@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		authService.logout(refreshToken);
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.ACCESS_TOKEN).toString());
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.REFRESH_TOKEN).toString());
		headers.add(HttpHeaders.SET_COOKIE, authCookies.expire(AuthCookies.GUEST_KEY).toString());
		return ApiResponse.success(SuccessStatus.LOGOUT_SUCCESS, null, headers);
	}

	private HttpHeaders tokenCookies(Tokens tokens) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE,
			authCookies.create(AuthCookies.ACCESS_TOKEN, tokens.accessToken(), jwtProvider.getAccessTokenTtl()).toString());
		headers.add(HttpHeaders.SET_COOKIE,
			authCookies.create(AuthCookies.REFRESH_TOKEN, tokens.refreshToken(), authTokenStore.getRefreshTokenTtl()).toString());
		return headers;
	}
}
