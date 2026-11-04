package com.health.jinryohanjang.domain.hospital.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:00에 병원 동기화를 실행한다. 병원 데이터가 비어 있으면 서버가 뜬 직후에도 한 번 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HospitalSyncScheduler {

	private final HospitalSyncService hospitalSyncService;

	@Value("${app.hospital-sync.run-on-startup:true}")
	private boolean runOnStartup;

	@Scheduled(cron = "${app.hospital-sync.cron}", zone = "Asia/Seoul")
	public void syncDaily() {
		hospitalSyncService.sync();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void syncOnStartupIfEmpty() {
		if (runOnStartup && hospitalSyncService.hasNoHospitals()) {
			log.info("병원 데이터가 비어 있어서 동기화를 바로 시작합니다.");
			Thread.ofVirtual().name("hospital-sync").start(hospitalSyncService::sync);
		}
	}
}
