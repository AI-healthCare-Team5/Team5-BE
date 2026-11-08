package com.health.jinryohanjang.domain.consent.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

	boolean existsByMemberIdAndConsentTypeAndPolicyVersionAndWithdrawnAtIsNull(
		Long memberId, ConsentType consentType, String policyVersion);

	/**
	 * 회원·종류별로 withdrawn_at이 없는 행은 하나뿐이다 (부분 UNIQUE 인덱스, data.sql).
	 */
	Optional<Consent> findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(Long memberId, ConsentType consentType);

	/**
	 * 탈퇴할 때 회원의 동의를 모두 철회한다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("UPDATE Consent c SET c.withdrawnAt = :now, c.updatedAt = :now "
		+ "WHERE c.member.id = :memberId AND c.withdrawnAt IS NULL")
	int withdrawAllByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
