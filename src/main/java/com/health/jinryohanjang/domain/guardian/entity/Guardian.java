package com.health.jinryohanjang.domain.guardian.entity;

import java.time.LocalDateTime;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.common.util.EncryptedStringConverter;
import com.health.jinryohanjang.domain.guardian.enums.Relation;
import com.health.jinryohanjang.domain.member.entity.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 보호자 연락처다. 회원당 1명이고, 전화번호는 숫자만 암호화해 저장한다.
 * 삭제하면 deleted_at을 기록하고, 다시 등록하면 같은 행을 되살려 덮어쓴다.
 */
@Getter
@Entity
@Table(name = "guardian", uniqueConstraints = @UniqueConstraint(name = "uk_guardian_member_id", columnNames = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "guardian_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Relation relation;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(nullable = false, columnDefinition = "TEXT")
	private String phoneNumber;

	private LocalDateTime deletedAt;

	private Guardian(Member member, Relation relation, String phoneNumber) {
		this.member = member;
		this.relation = relation;
		this.phoneNumber = phoneNumber;
	}

	public static Guardian register(Member member, Relation relation, String phoneNumber) {
		return new Guardian(member, relation, phoneNumber);
	}

	/**
	 * 지워진 행이어도 되살려 새 값으로 덮어쓴다.
	 */
	public void change(Relation relation, String phoneNumber) {
		this.relation = relation;
		this.phoneNumber = phoneNumber;
		this.deletedAt = null;
	}

	public void delete(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	/**
	 * 응급 카드에 쓰는 전체 번호다. 예: 010-1234-5678
	 */
	public String formattedPhoneNumber() {
		String[] parts = splitPhoneNumber();
		return String.join("-", parts);
	}

	/**
	 * 내 정보·보호자 등록 응답에 쓰는 가운데를 가린 번호다. 예: 010-****-5678
	 */
	public String maskedPhoneNumber() {
		String[] parts = splitPhoneNumber();
		return parts[0] + "-" + "*".repeat(parts[1].length()) + "-" + parts[2];
	}

	/**
	 * 앞·가운데·끝 4자리로 나눈다. 02로 시작하는 10자리는 2-4-4, 그 외 10자리는 3-3-4, 11자리는 3-4-4다.
	 */
	private String[] splitPhoneNumber() {
		int length = phoneNumber.length();
		int prefixLength = phoneNumber.startsWith("02") && length == 10 ? 2 : 3;
		return new String[] {
			phoneNumber.substring(0, prefixLength),
			phoneNumber.substring(prefixLength, length - 4),
			phoneNumber.substring(length - 4)
		};
	}
}
