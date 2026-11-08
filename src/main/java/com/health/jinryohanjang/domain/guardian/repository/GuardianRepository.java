package com.health.jinryohanjang.domain.guardian.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.guardian.entity.Guardian;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

	/**
	 * 지워진 행도 함께 찾는다. 다시 등록할 때 같은 행을 되살리기 위해서다.
	 */
	Optional<Guardian> findByMemberId(Long memberId);

	Optional<Guardian> findByMemberIdAndDeletedAtIsNull(Long memberId);
}
