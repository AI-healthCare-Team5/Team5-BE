package com.health.jinryohanjang.domain.member.entity;

import java.time.LocalDateTime;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.domain.member.enums.MemberStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 계정으로 가입한 회원이다.
 * 탈퇴하면 status = WITHDRAWN과 deleted_at을 기록하고, 30일 안에 같은 카카오 계정으로 오면 이 행을 되살린다.
 */
@Getter
@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_kakao_id", columnNames = "kakao_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;

	@Column(name = "kakao_id", nullable = false, length = 20)
	private String kakaoId;

	@Column(length = 30)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberStatus status;

	private LocalDateTime deletedAt;

	private Member(String kakaoId, String nickname) {
		this.kakaoId = kakaoId;
		this.nickname = nickname;
		this.status = MemberStatus.ACTIVE;
	}

	public static Member join(String kakaoId, String nickname) {
		return new Member(kakaoId, nickname);
	}

	public boolean isWithdrawn() {
		return status == MemberStatus.WITHDRAWN;
	}

	/**
	 * 탈퇴한다. 30일 안에 같은 카카오 계정으로 다시 로그인하면 restore로 이 행만 되살린다.
	 */
	public void withdraw(LocalDateTime deletedAt) {
		this.status = MemberStatus.WITHDRAWN;
		this.deletedAt = deletedAt;
	}

	public void restore(String nickname) {
		this.status = MemberStatus.ACTIVE;
		this.deletedAt = null;
		this.nickname = nickname;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}
}
