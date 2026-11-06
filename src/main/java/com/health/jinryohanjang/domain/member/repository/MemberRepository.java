package com.health.jinryohanjang.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.health.jinryohanjang.domain.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	/**
	 * 탈퇴한 회원도 함께 찾는다. 재가입 때 같은 행을 되살리기 위해서다.
	 */
	Optional<Member> findByKakaoId(String kakaoId);
}
