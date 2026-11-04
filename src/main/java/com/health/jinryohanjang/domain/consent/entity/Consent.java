package com.health.jinryohanjang.domain.consent.entity;

import java.time.LocalDateTime;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.member.entity.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 동의 기록이다. withdrawn_at이 없는 행이 현재 유효한 동의다.
 */
@Getter
@Entity
@Table(name = "consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "consent_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConsentType consentType;

	@Column(nullable = false, length = 20)
	private String policyVersion;

	@Column(nullable = false)
	private LocalDateTime agreedAt;

	private LocalDateTime withdrawnAt;
}
