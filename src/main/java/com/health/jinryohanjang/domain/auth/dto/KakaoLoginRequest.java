package com.health.jinryohanjang.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
	@Schema(description = "카카오가 프론트 콜백 주소로 넘겨준 인가 코드")
	@NotBlank(message = "인가 코드가 없어요")
	String code,

	@Schema(description = "카카오가 콜백으로 같이 넘겨준 state. oauthState 쿠키와 같아야 한다.")
	@NotBlank(message = "state가 없어요")
	String state,

	@Schema(description = "인가 요청 때 쓴 콜백 주소. 서버 허용 목록에 있어야 한다.",
		example = "https://jinryohanjang.pages.dev/auth/kakao/callback")
	@NotBlank(message = "redirectUri가 없어요")
	String redirectUri
) {
}
