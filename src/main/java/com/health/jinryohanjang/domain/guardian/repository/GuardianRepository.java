package com.health.jinryohanjang.domain.guardian.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.guardian.entity.Guardian;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

	/**
	 * 지워진 행도 함께 찾는다. 다시 등록할 때 같은 행을 되살리기 위해서다.
	 */
	Optional<Guardian> findByMemberId(Long memberId);

	Optional<Guardian> findByMemberIdAndDeletedAtIsNull(Long memberId);

	/**
	 * 탈퇴할 때 보호자를 지운다. 암호화 컬럼을 복호화하지 않도록 엔티티를 불러오지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("UPDATE Guardian g SET g.deletedAt = :now, g.updatedAt = :now "
		+ "WHERE g.member.id = :memberId AND g.deletedAt IS NULL")
	int deleteByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
