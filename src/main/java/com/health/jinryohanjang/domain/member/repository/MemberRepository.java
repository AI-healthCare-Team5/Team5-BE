package com.health.jinryohanjang.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.enums.MemberStatus;

public interface MemberRepository extends JpaRepository<Member, Long> {

	/**
	 * 탈퇴한 회원도 함께 찾는다. 재가입 때 같은 행을 되살리기 위해서다.
	 */
	Optional<Member> findByKakaoId(String kakaoId);

	/**
	 * 30일 삭제 배치: 탈퇴한 지 cutoff가 지난 회원을 지운다. 기록·보호자·동의를 먼저 지운 뒤에 부른다.
	 */
	@Modifying
	@Query("DELETE FROM Member m WHERE m.status = :withdrawn AND m.deletedAt < :cutoff")
	int purgeWithdrawn(@Param("cutoff") LocalDateTime cutoff, @Param("withdrawn") MemberStatus withdrawn);
}
