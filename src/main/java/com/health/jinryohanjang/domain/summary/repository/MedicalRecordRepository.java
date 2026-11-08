package com.health.jinryohanjang.domain.summary.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.summary.entity.MedicalRecord;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

	/**
	 * 지운 기록도 함께 찾는다. 같은 요약지를 다시 보관하지 않게 하기 위해서다.
	 */
	Optional<MedicalRecord> findBySourceSummaryId(String sourceSummaryId);

	@EntityGraph(attributePaths = "department")
	Page<MedicalRecord> findAllByMemberIdAndDeletedAtIsNull(Long memberId, Pageable pageable);

	long countByMemberIdAndDeletedAtIsNull(Long memberId);

	/**
	 * 탈퇴할 때 회원의 기록을 모두 지운다. 암호화 컬럼을 복호화하지 않도록 엔티티를 불러오지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("UPDATE MedicalRecord r SET r.deletedAt = :now, r.updatedAt = :now "
		+ "WHERE r.member.id = :memberId AND r.deletedAt IS NULL")
	int deleteAllByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
