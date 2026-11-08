package com.health.jinryohanjang.domain.member.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 03:00에 30일 지난 데이터를 완전히 지운다. 병원 동기화(04:00)와 겹치지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPurgeScheduler {

	private final DataPurgeService dataPurgeService;

	@Scheduled(cron = "${app.purge.cron}", zone = "Asia/Seoul")
	public void purgeDaily() {
		try {
			dataPurgeService.purge();
		} catch (RuntimeException e) {
			log.error("30일 지난 데이터 삭제 실패, 다음 실행 때 다시 시도합니다.", e);
		}
	}
}
