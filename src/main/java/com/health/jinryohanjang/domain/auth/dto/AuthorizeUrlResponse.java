package com.health.jinryohanjang.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthorizeUrlResponse(
	@Schema(description = "카카오 인가 URL. 프론트는 이 URL로 이동한다.",
		example = "https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&state=Zq3v...k1")
	String authorizeUrl
) {
}
