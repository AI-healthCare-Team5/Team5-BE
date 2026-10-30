package com.health.jinryohanjang.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.health.jinryohanjang.global.common.response.Domain;

class RateLimiterTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final RateLimiter rateLimiter = new RateLimiter(redisTemplate);

	@Test
	void 제한_이하면_통과한다() {
		givenCount(10L, 30_000L);

		assertThatCode(() -> rateLimiter.check(Domain.INTAKE, "ip:1", 10, Duration.ofMinutes(1)))
			.doesNotThrowAnyException();
	}

	@Test
	void 제한을_넘으면_도메인과_남은_초를_담아_던진다() {
		givenCount(11L, 12_300L);

		RateLimitException e = catchThrowableOfType(RateLimitException.class,
			() -> rateLimiter.check(Domain.STT, "ip:1", 10, Duration.ofMinutes(1)));

		assertThat(e.getDomain()).isEqualTo(Domain.STT);
		assertThat(e.getRetryAfterSeconds()).isEqualTo(13L);
	}

	@SuppressWarnings("unchecked")
	private void givenCount(long count, long remainingMillis) {
		given(redisTemplate.execute(any(RedisScript.class), anyList(), eq("60000")))
			.willReturn(List.of(count, remainingMillis));
	}
}
