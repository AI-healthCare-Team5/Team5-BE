package com.health.jinryohanjang.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.guardian.repository.GuardianRepository;
import com.health.jinryohanjang.domain.member.enums.MemberStatus;
import com.health.jinryohanjang.domain.member.repository.MemberRepository;
import com.health.jinryohanjang.domain.summary.repository.MedicalRecordRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * soft delete한 지 30일이 지난 데이터를 완전히 지운다 (노션 "백엔드 결정 사항", 동의 문구 C01·C02).
 * - 지운 기록·보호자, 탈퇴한 회원의 기록·보호자·동의·회원 행
 * - 회원이 남아 있는 동안 철회한 동의 이력은 지우지 않는다.
 * 외래 키 순서(자식 → 부모)대로 JPQL 일괄 DELETE로 지워서 암호화 컬럼을 복호화하지 않는다.
 */
@Slf4j
@Service
public class DataPurgeService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final MedicalRecordRepository medicalRecordRepository;
	private final GuardianRepository guardianRepository;
	private final ConsentRepository consentRepository;
	private final MemberRepository memberRepository;
	private final Duration retention;

	public DataPurgeService(
		MedicalRecordRepository medicalRecordRepository,
		GuardianRepository guardianRepository,
		ConsentRepository consentRepository,
		MemberRepository memberRepository,
		@Value("${app.purge.retention:30d}") Duration retention) {
		this.medicalRecordRepository = medicalRecordRepository;
		this.guardianRepository = guardianRepository;
		this.consentRepository = consentRepository;
		this.memberRepository = memberRepository;
		this.retention = retention;
	}

	@Transactional
	public void purge() {
		LocalDateTime cutoff = LocalDateTime.now(SEOUL).minus(retention);
		MemberStatus withdrawn = MemberStatus.WITHDRAWN;
		int healthInfos = medicalRecordRepository.purgeHealthInfos(cutoff, withdrawn);
		int items = medicalRecordRepository.purgeItems(cutoff, withdrawn);
		int records = medicalRecordRepository.purgeRecords(cutoff, withdrawn);
		int guardians = guardianRepository.purge(cutoff, withdrawn);
		int consents = consentRepository.purgeOfWithdrawnMembers(cutoff, withdrawn);
		int members = memberRepository.purgeWithdrawn(cutoff, withdrawn);
		log.info("30일 지난 데이터 완전 삭제: 기록 {}(문답 {}, 건강정보 {}), 보호자 {}, 동의 {}, 회원 {}",
			records, items, healthInfos, guardians, consents, members);
	}
}
