package com.health.jinryohanjang.global.ratelimit;

import org.springframework.http.HttpStatus;

import com.health.jinryohanjang.global.common.response.Domain;
import com.health.jinryohanjang.global.error.BusinessException;

import lombok.Getter;

/**
 * 호출 제한 초과. 응답은 {도메인}_429와 Retry-After 헤더(초)
 */
@Getter
public class RateLimitException extends BusinessException {

	private static final String MESSAGE = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";

	private final long retryAfterSeconds;

	public RateLimitException(Domain domain, long retryAfterSeconds) {
		super(domain, HttpStatus.TOO_MANY_REQUESTS, MESSAGE);
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
