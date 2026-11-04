package com.health.jinryohanjang.domain.auth.security;

import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 로그인 accessToken(JWT)을 만들고 검증한다. subject에 회원 ID를 담는다.
 */
@Component
public class JwtProvider {

	private static final int MIN_KEY_BYTES = 32;

	private final SecretKey key;
	private final Duration accessTokenTtl;

	public JwtProvider(
		@Value("${app.jwt.secret:}") String encodedSecret,
		@Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl) {
		this.key = toKey(encodedSecret);
		this.accessTokenTtl = accessTokenTtl;
	}

	public String createAccessToken(Long memberId) {
		Date now = new Date();
		return Jwts.builder()
			.subject(String.valueOf(memberId))
			.issuedAt(now)
			.expiration(new Date(now.getTime() + accessTokenTtl.toMillis()))
			.signWith(key)
			.compact();
	}

	public Long getMemberId(String accessToken) {
		try {
			String subject = Jwts.parser().verifyWith(key).build()
				.parseSignedClaims(accessToken)
				.getPayload()
				.getSubject();
			return Long.valueOf(subject);
		} catch (ExpiredJwtException e) {
			throw new GeneralException(ErrorStatus.TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new GeneralException(ErrorStatus.TOKEN_INVALID);
		}
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	private static SecretKey toKey(String encodedSecret) {
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(encodedSecret);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("JWT_SECRET은 Base64 값이어야 합니다.", e);
		}
		if (keyBytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("JWT_SECRET은 32바이트 이상을 Base64로 인코딩한 값이어야 합니다.");
		}
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
