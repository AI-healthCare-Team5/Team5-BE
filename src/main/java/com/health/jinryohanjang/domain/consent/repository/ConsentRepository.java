package com.health.jinryohanjang.domain.consent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

	boolean existsByMemberIdAndConsentTypeAndPolicyVersionAndWithdrawnAtIsNull(
		Long memberId, ConsentType consentType, String policyVersion);
}
