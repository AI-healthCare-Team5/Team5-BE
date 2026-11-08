package com.health.jinryohanjang.domain.consent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

	boolean existsByMemberIdAndConsentTypeAndPolicyVersionAndWithdrawnAtIsNull(
		Long memberId, ConsentType consentType, String policyVersion);

	/**
	 * 회원·종류별로 withdrawn_at이 없는 행은 하나뿐이다 (부분 UNIQUE 인덱스, data.sql).
	 */
	Optional<Consent> findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(Long memberId, ConsentType consentType);
}
