package com.health.jinryohanjang.domain.intake.entity;

import com.health.jinryohanjang.common.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 위험 신호 규칙의 키워드다. 같은 groupNo끼리 묶인다.
 */
@Getter
@Entity
@Table(name = "red_flag_keyword")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedFlagKeyword extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "red_flag_keyword_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "red_flag_rule_id", nullable = false)
	private RedFlagRule rule;

	@Column(nullable = false)
	private int groupNo;

	@Column(nullable = false, length = 50)
	private String keyword;
}
