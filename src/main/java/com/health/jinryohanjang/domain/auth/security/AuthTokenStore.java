package com.health.jinryohanjang.domain.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * refreshToken과 탈퇴 회원 차단 키를 Redis에 저장한다.
 * - refresh:{memberId} : 현재 유효한 refreshToken (14일, 재발급 때마다 교체)
 * - withdrawn:{memberId} : 탈퇴 직후 남은 accessToken을 막는 키 (accessToken 수명만큼)
 * refreshToken 쿠키 값은 "{memberId}.{임의 문자열}"이다.
 */
@Component
public class AuthTokenStore {

	private static final String REFRESH_KEY_PREFIX = "refresh:";
	private static final String WITHDRAWN_KEY_PREFIX = "withdrawn:";
	private static final int RANDOM_BYTES = 32;

	private final StringRedisTemplate redisTemplate;
	private final Duration refreshTokenTtl;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthTokenStore(
		StringRedisTemplate redisTemplate,
		@Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
		this.redisTemplate = redisTemplate;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public String issueRefreshToken(Long memberId) {
		byte[] random = new byte[RANDOM_BYTES];
		secureRandom.nextBytes(random);
		String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + memberId, secret, refreshTokenTtl);
		return memberId + "." + secret;
	}

	/**
	 * Redis에 저장된 값과 같을 때만 회원 ID를 돌려준다.
	 */
	public Optional<Long> findMemberId(String refreshToken) {
		if (refreshToken == null) {
			return Optional.empty();
		}
		int separator = refreshToken.indexOf('.');
		if (separator <= 0) {
			return Optional.empty();
		}
		Long memberId;
		try {
			memberId = Long.valueOf(refreshToken.substring(0, separator));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		String saved = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + memberId);
		if (saved == null || !saved.equals(refreshToken.substring(separator + 1))) {
			return Optional.empty();
		}
		return Optional.of(memberId);
	}

	public void deleteRefreshToken(Long memberId) {
		redisTemplate.delete(REFRESH_KEY_PREFIX + memberId);
	}

	public boolean isWithdrawn(Long memberId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(WITHDRAWN_KEY_PREFIX + memberId));
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}
}
