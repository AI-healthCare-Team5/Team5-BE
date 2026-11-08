package com.health.jinryohanjang.domain.consent.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.consent.entity.Consent;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.member.enums.MemberStatus;

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

	/**
	 * 30일 삭제 배치: 탈퇴한 지 cutoff가 지난 회원의 동의를 지운다. 회원이 남아 있는 동안 철회한 동의 이력은 지우지 않는다.
	 */
	@Modifying
	@Query("DELETE FROM Consent c "
		+ "WHERE c.member.id IN (SELECT m.id FROM Member m WHERE m.status = :withdrawn AND m.deletedAt < :cutoff)")
	int purgeOfWithdrawnMembers(@Param("cutoff") LocalDateTime cutoff, @Param("withdrawn") MemberStatus withdrawn);
}
