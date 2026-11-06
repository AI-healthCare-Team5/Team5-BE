package com.health.jinryohanjang.domain.summary.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.summary.model.Summary;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 보관 전 요약지를 Redis에 저장한다. 문진과 같이 마지막 요청부터 1시간 동안 남는다.
 * - summary:{summaryId} : 요약지(JSON)
 * - guest-summaries:{guestKey} : 그 guestKey의 요약지 ID 목록 (로그아웃 때 함께 지운다)
 */
@Repository
@RequiredArgsConstructor
public class SummaryStore {

	private static final String KEY_PREFIX = "summary:";
	private static final String GUEST_KEY_PREFIX = "guest-summaries:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public Optional<Summary> find(String summaryId) {
		String json = redisTemplate.opsForValue().get(KEY_PREFIX + summaryId);
		return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, Summary.class));
	}

	public void save(Summary summary) {
		redisTemplate.opsForValue().set(KEY_PREFIX + summary.getSummaryId(), objectMapper.writeValueAsString(summary),
			IntakeStore.TTL);
		String guestKey = GUEST_KEY_PREFIX + summary.getGuestKey();
		redisTemplate.opsForSet().add(guestKey, summary.getSummaryId());
		redisTemplate.expire(guestKey, IntakeStore.TTL);
	}

	/**
	 * 내용은 그대로 두고 남은 시간만 1시간으로 늘린다. 생성 중인 작업의 저장을 덮어쓰지 않으려고 따로 둔다.
	 */
	public void touch(Summary summary) {
		redisTemplate.expire(KEY_PREFIX + summary.getSummaryId(), IntakeStore.TTL);
		redisTemplate.expire(GUEST_KEY_PREFIX + summary.getGuestKey(), IntakeStore.TTL);
	}

	public void delete(String summaryId, String guestKey) {
		redisTemplate.delete(KEY_PREFIX + summaryId);
		redisTemplate.opsForSet().remove(GUEST_KEY_PREFIX + guestKey, summaryId);
	}

	public void deleteAllByGuestKey(String guestKey) {
		Set<String> summaryIds = redisTemplate.opsForSet().members(GUEST_KEY_PREFIX + guestKey);
		if (summaryIds != null) {
			summaryIds.forEach(summaryId -> redisTemplate.delete(KEY_PREFIX + summaryId));
		}
		redisTemplate.delete(GUEST_KEY_PREFIX + guestKey);
	}
}
