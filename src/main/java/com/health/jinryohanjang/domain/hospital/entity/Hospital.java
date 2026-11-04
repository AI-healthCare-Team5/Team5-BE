package com.health.jinryohanjang.domain.hospital.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.health.jinryohanjang.common.base.BaseEntity;
import com.health.jinryohanjang.domain.department.entity.Department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터에서 동기화한 병원이다. API는 이 테이블만 조회한다.
 */
@Getter
@Entity
@Table(name = "hospital", uniqueConstraints = @UniqueConstraint(name = "uk_hospital_ykiho", columnNames = "ykiho"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hospital extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "hospital_id")
	private Long id;

	@Column(nullable = false, length = 100)
	private String ykiho;

	@Column(length = 20)
	private String hpid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "region_code", nullable = false)
	private Region region;

	@Column(name = "hospital_name", nullable = false, length = 100)
	private String name;

	@Column(length = 20)
	private String phoneNumber;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(nullable = false)
	private boolean hasEmergencyRoom;

	@Column(nullable = false)
	private LocalDateTime syncedAt;

	@Column(nullable = false, length = 20)
	private String hospitalType;

	@ManyToMany
	@JoinTable(name = "hospital_department",
		joinColumns = @JoinColumn(name = "hospital_id"),
		inverseJoinColumns = @JoinColumn(name = "department_code"))
	@OrderBy("code")
	private Set<Department> departments = new LinkedHashSet<>();

	@OneToMany(mappedBy = "hospital")
	private List<HospitalSchedule> schedules = new ArrayList<>();
}
