package com.health.jinryohanjang.domain.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인한 회원을 찾는다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;

	/**
	 * accessToken이 유효해도 회원 행이 없거나 탈퇴했으면 AUTH_401이다.
	 */
	@Transactional(readOnly = true)
	public Member getActiveMember(Long memberId) {
		return memberRepository.findById(memberId)
			.filter(member -> !member.isWithdrawn())
			.orElseThrow(() -> new GeneralException(ErrorStatus.TOKEN_INVALID));
	}
}
