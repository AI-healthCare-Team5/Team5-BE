package com.health.jinryohanjang.domain.auth.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키를 만든다. 모두 HttpOnly, Secure, SameSite=Lax, Path=/이다.
 */
@Component
public class AuthCookies {

	public static final String ACCESS_TOKEN = "accessToken";
	public static final String REFRESH_TOKEN = "refreshToken";
	public static final String GUEST_KEY = "guestKey";
	public static final String OAUTH_STATE = "oauthState";

	public static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);

	private final boolean secure;

	public AuthCookies(@Value("${app.cookie.secure:true}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie create(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

	public ResponseCookie expire(String name) {
		return create(name, "", Duration.ZERO);
	}
}
