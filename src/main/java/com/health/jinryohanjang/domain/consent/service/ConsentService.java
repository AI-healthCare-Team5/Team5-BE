package com.health.jinryohanjang.domain.consent.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.member.entity.Member;

import lombok.RequiredArgsConstructor;

/**
 * 동의를 저장하고 철회한다. 회원·종류별로 withdrawn_at이 없는 행 하나가 현재 유효한 동의다.
 * 버전 확인은 부르는 쪽에서 한다 (실패 code의 도메인이 API마다 달라서).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ConsentRepository consentRepository;

	/**
	 * 같은 버전에 이미 동의해 있으면 그 동의를 그대로 돌려준다. 예전 버전 동의는 철회하고 새로 저장한다.
	 */
	@Transactional
	public AgreeResult agree(Member member, ConsentType consentType, String policyVersion) {
		Optional<Consent> current = consentRepository.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(
			member.getId(), consentType);
		if (current.isPresent() && current.get().getPolicyVersion().equals(policyVersion)) {
			return new AgreeResult(current.get(), false);
		}
		LocalDateTime now = now();
		if (current.isPresent()) {
			current.get().withdraw(now);
			// 부분 UNIQUE 인덱스에 걸리지 않도록 철회를 새 행 INSERT보다 먼저 반영한다.
			consentRepository.flush();
		}
		Consent consent = consentRepository.save(Consent.agree(member, consentType, policyVersion, now));
		return new AgreeResult(consent, true);
	}

	@Transactional
	public void withdraw(Long memberId, ConsentType consentType) {
		consentRepository.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, consentType)
			.ifPresent(consent -> consent.withdraw(now()));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(SEOUL).withNano(0);
	}

	public record AgreeResult(Consent consent, boolean created) {
	}
}
