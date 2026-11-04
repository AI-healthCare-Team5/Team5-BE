package com.health.jinryohanjang.domain.department.entity;

import com.health.jinryohanjang.common.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 심평원 진료과목이다. 기초 데이터(data.sql)로 넣는다.
 */
@Getter
@Entity
@Table(name = "department")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseEntity {

	@Id
	@Column(name = "department_code", length = 10)
	private String code;

	@Column(name = "department_name", nullable = false, length = 30)
	private String name;
}
