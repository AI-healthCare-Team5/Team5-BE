package com.health.jinryohanjang.domain.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.auth.security.AuthTokenStore;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.guardian.repository.GuardianRepository;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.member.dto.MemberInfoResponse;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.repository.MemberRepository;
import com.health.jinryohanjang.domain.summary.repository.MedicalRecordRepository;
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인한 회원을 찾고, 내 정보 조회와 탈퇴를 처리한다.
 * 보호자·동의·기록 서비스가 이 서비스를 쓰므로, 순환 의존이 생기지 않게 다른 도메인은 리포지토리로 다룬다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String PROVIDER = "KAKAO";

	private final MemberRepository memberRepository;
	private final ConsentRepository consentRepository;
	private final GuardianRepository guardianRepository;
	private final MedicalRecordRepository medicalRecordRepository;
	private final AuthTokenStore authTokenStore;
	private final IntakeStore intakeStore;
	private final SummaryStore summaryStore;

	/**
	 * accessToken이 유효해도 회원 행이 없거나 탈퇴했으면 AUTH_401이다.
	 */
	@Transactional(readOnly = true)
	public Member getActiveMember(Long memberId) {
		return memberRepository.findById(memberId)
			.filter(member -> !member.isWithdrawn())
			.orElseThrow(() -> new GeneralException(ErrorStatus.TOKEN_INVALID));
	}

	/**
	 * 동의는 현재 유효한(철회하지 않은) 행 기준이다. 보호자 번호는 가운데를 가린다.
	 */
	@Transactional(readOnly = true)
	public MemberInfoResponse getMyInfo(Long memberId) {
		Member member = getActiveMember(memberId);
		MemberInfoResponse.Consents consents = new MemberInfoResponse.Consents(
			consentStatus(memberId, ConsentType.RECORD), consentStatus(memberId, ConsentType.GUARDIAN));
		MemberInfoResponse.Guardian guardian = guardianRepository.findByMemberIdAndDeletedAtIsNull(memberId)
			.map(found -> new MemberInfoResponse.Guardian(found.getRelation(), found.maskedPhoneNumber()))
			.orElse(null);
		return new MemberInfoResponse(member.getId(), member.getNickname(), PROVIDER, consents,
			medicalRecordRepository.countByMemberIdAndDeletedAtIsNull(memberId), guardian);
	}

	/**
	 * 회원은 WITHDRAWN + deleted_at, 기록·보호자는 deleted_at, 동의는 withdrawn_at을 기록한다.
	 * refreshToken을 지우고, 이미 발급한 accessToken은 만료될 때까지 막는다. 이 기기의 문진·요약지도 지운다.
	 * Redis 작업은 DB 커밋이 끝난 뒤에 해서, 커밋이 실패했는데 토큰만 막히는 상태가 생기지 않게 한다.
	 */
	@Transactional
	public void withdraw(Long memberId, String guestKey) {
		Member member = getActiveMember(memberId);
		LocalDateTime now = LocalDateTime.now(SEOUL).withNano(0);
		medicalRecordRepository.deleteAllByMemberId(memberId, now);
		guardianRepository.deleteByMemberId(memberId, now);
		consentRepository.withdrawAllByMemberId(memberId, now);
		member.withdraw(now);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				clearSessions(memberId, guestKey);
			}
		});
	}

	/**
	 * 탈퇴는 이미 커밋됐으므로 실패해도 응답은 성공으로 둔다. 남은 accessToken은 서비스의 회원 확인(DB 상태)에서도 막힌다.
	 */
	private void clearSessions(Long memberId, String guestKey) {
		try {
			authTokenStore.deleteRefreshToken(memberId);
			authTokenStore.markWithdrawn(memberId);
			if (StringUtils.hasText(guestKey)) {
				intakeStore.deleteAllByGuestKey(guestKey);
				summaryStore.deleteAllByGuestKey(guestKey);
			}
		} catch (RuntimeException e) {
			log.error("탈퇴는 끝났지만 Redis의 토큰·문진을 정리하지 못했습니다.", e);
		}
	}

	private MemberInfoResponse.ConsentStatus consentStatus(Long memberId, ConsentType consentType) {
		return consentRepository.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, consentType)
			.map(consent -> new MemberInfoResponse.ConsentStatus(true, consent.getPolicyVersion(),
				consent.getAgreedAt().atZone(SEOUL).toOffsetDateTime()))
			.orElse(new MemberInfoResponse.ConsentStatus(false, null, null));
	}
}
