package com.health.jinryohanjang.global.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.health.jinryohanjang.global.common.response.Domain;

import lombok.RequiredArgsConstructor;

/**
 * Redis 고정 구간 호출 제한
 * 구간 안 호출 수가 limit을 넘으면 {도메인}_429와 남은 시간(Retry-After)을 응답한다.
 * 예: rateLimiter.check(Domain.INTAKE, "intake-start:ip:" + ip, 10, Duration.ofMinutes(1))
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

	private static final String KEY_PREFIX = "rate:";

	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> INCREMENT_SCRIPT = RedisScript.of("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then
			redis.call('PEXPIRE', KEYS[1], ARGV[1])
		end
		return {count, redis.call('PTTL', KEYS[1])}
		""", List.class);

	private final StringRedisTemplate redisTemplate;

	public void check(Domain domain, String key, int limit, Duration window) {
		List<?> result = redisTemplate.execute(INCREMENT_SCRIPT, List.of(KEY_PREFIX + key),
			String.valueOf(window.toMillis()));
		long count = ((Number)result.get(0)).longValue();
		if (count <= limit) {
			return;
		}
		long remainingMillis = ((Number)result.get(1)).longValue();
		long retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1000);
		throw new RateLimitException(domain, retryAfterSeconds);
	}
}
