package com.health.jinryohanjang.domain.intake.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.health.jinryohanjang.domain.intake.model.IntakeSession;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 문진을 Redis에 저장한다. 마지막 요청부터 1시간 동안 남고, 저장할 때마다 다시 1시간으로 늘어난다.
 * - intake:{intakeId} : 문진 상태(JSON)
 * - guest-intakes:{guestKey} : 그 guestKey의 문진 ID 목록 (로그아웃 때 함께 지운다)
 * - intake-lock:{intakeId} : 같은 문진에 답변이 동시에 들어오는 것을 막는 잠금
 */
@Repository
@RequiredArgsConstructor
public class IntakeStore {

	public static final Duration TTL = Duration.ofHours(1);
	private static final Duration LOCK_TTL = Duration.ofSeconds(30);
	private static final String KEY_PREFIX = "intake:";
	private static final String GUEST_KEY_PREFIX = "guest-intakes:";
	private static final String LOCK_KEY_PREFIX = "intake-lock:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public Optional<IntakeSession> find(String intakeId) {
		String json = redisTemplate.opsForValue().get(KEY_PREFIX + intakeId);
		return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, IntakeSession.class));
	}

	public void save(IntakeSession session) {
		redisTemplate.opsForValue().set(KEY_PREFIX + session.getIntakeId(), objectMapper.writeValueAsString(session), TTL);
		String guestKey = GUEST_KEY_PREFIX + session.getGuestKey();
		redisTemplate.opsForSet().add(guestKey, session.getIntakeId());
		redisTemplate.expire(guestKey, TTL);
	}

	public void delete(IntakeSession session) {
		redisTemplate.delete(KEY_PREFIX + session.getIntakeId());
		redisTemplate.opsForSet().remove(GUEST_KEY_PREFIX + session.getGuestKey(), session.getIntakeId());
	}

	public void deleteAllByGuestKey(String guestKey) {
		Set<String> intakeIds = redisTemplate.opsForSet().members(GUEST_KEY_PREFIX + guestKey);
		if (intakeIds != null) {
			intakeIds.forEach(intakeId -> redisTemplate.delete(KEY_PREFIX + intakeId));
		}
		redisTemplate.delete(GUEST_KEY_PREFIX + guestKey);
	}

	public boolean lock(String intakeId) {
		return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + intakeId, "1", LOCK_TTL));
	}

	public void unlock(String intakeId) {
		redisTemplate.delete(LOCK_KEY_PREFIX + intakeId);
	}
}
