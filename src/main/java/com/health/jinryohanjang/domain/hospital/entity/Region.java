package com.health.jinryohanjang.domain.hospital.entity;

import java.math.BigDecimal;

import com.health.jinryohanjang.common.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 강원 시·군이다. 위치가 없을 때 중심 좌표로 병원을 찾는다. 기초 데이터(data.sql)로 넣는다.
 */
@Getter
@Entity
@Table(name = "region")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

	@Id
	@Column(name = "region_code", length = 10)
	private String code;

	@Column(name = "region_name", nullable = false, length = 20)
	private String name;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal centerLatitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal centerLongitude;
}
