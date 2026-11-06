package com.health.jinryohanjang.domain.intake.entity;

import java.util.ArrayList;
import java.util.List;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.domain.intake.enums.RedFlagCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 위험 신호 규칙이다. 같은 group_no 키워드는 OR, 다른 group_no끼리는 AND로 판정한다.
 * 여러 규칙이 걸리면 priority가 가장 작은 규칙의 category를 쓴다. 기초 데이터(data.sql)로 넣는다.
 */
@Getter
@Entity
@Table(name = "red_flag_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedFlagRule extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "red_flag_rule_id")
	private Long id;

	@Column(nullable = false, length = 50)
	private String ruleName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RedFlagCategory category;

	@Column(nullable = false)
	private int priority;

	@Column(nullable = false)
	private boolean isActive;

	@OneToMany(mappedBy = "rule")
	private List<RedFlagKeyword> keywords = new ArrayList<>();
}
