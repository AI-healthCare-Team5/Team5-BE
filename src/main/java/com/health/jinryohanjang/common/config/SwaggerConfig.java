package com.health.jinryohanjang.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger 문서의 공통 정보와 쿠키 인증 방식을 정한다.
 * API별로 필요한 쿠키는 @SecurityRequirement에 아래 이름을 적는다.
 */
@Configuration
public class SwaggerConfig {

	public static final String ACCESS_TOKEN = "accessToken";
	public static final String REFRESH_TOKEN = "refreshToken";
	public static final String GUEST_KEY = "guestKey";
	public static final String OAUTH_STATE = "oauthState";

	private static final String DESCRIPTION = """
		진료한장 백엔드 API입니다. 노션 "진료한장 도메인별 API 목록"의 공통 규칙을 따릅니다.

		- 응답은 성공·실패 모두 `isSuccess`, `code`, `message`, `result`입니다. code는 `{도메인}_{HTTP 상태}`입니다.
		- 401은 어느 API든 `AUTH_401`입니다. 받으면 `POST /api/auth/refresh`를 한 번 부르고 원래 요청을 다시 보냅니다.
		- 400의 `result.errors`는 `[{field, reason}]`이고, 필드를 알 수 없으면 빈 배열입니다.
		- 인증은 쿠키입니다. 프론트가 `/api`를 프록시하므로 같은 도메인에서 쿠키가 자동으로 붙습니다.
		- 아직 구현하지 않은 API는 `COMMON_501`로 응답합니다.
		""";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("진료한장 API").version("v1").description(DESCRIPTION))
			.addServersItem(new Server().url("/"))
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN, cookie(ACCESS_TOKEN, "로그인 JWT (30분)"))
				.addSecuritySchemes(REFRESH_TOKEN, cookie(REFRESH_TOKEN, "재발급 토큰 (14일)"))
				.addSecuritySchemes(GUEST_KEY, cookie(GUEST_KEY, "문진·요약지 본인 확인 키 (1시간)"))
				.addSecuritySchemes(OAUTH_STATE, cookie(OAUTH_STATE, "카카오 로그인 state (10분)")));
	}

	private SecurityScheme cookie(String name, String description) {
		return new SecurityScheme()
			.type(SecurityScheme.Type.APIKEY)
			.in(SecurityScheme.In.COOKIE)
			.name(name)
			.description(description);
	}
}
