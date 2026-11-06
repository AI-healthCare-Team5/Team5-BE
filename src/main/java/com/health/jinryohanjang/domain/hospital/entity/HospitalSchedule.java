package com.health.jinryohanjang.domain.hospital.entity;

import java.time.LocalTime;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 병원의 요일별 진료 시간이다. 요일은 1=월 ~ 7=일, 8=공휴일이다.
 * 그 요일 행이 없으면 휴진이고, 24시간은 00:00~23:59로 저장한다.
 */
@Getter
@Entity
@Table(name = "hospital_schedule", uniqueConstraints = @UniqueConstraint(
	name = "uk_hospital_schedule_day", columnNames = {"hospital_id", "day_of_week"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalSchedule extends BaseEntity {

	public static final int HOLIDAY = 8;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "hospital_schedule_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hospital_id", nullable = false)
	private Hospital hospital;

	@Column(nullable = false)
	private int dayOfWeek;

	@Column(nullable = false)
	private LocalTime openTime;

	@Column(nullable = false)
	private LocalTime closeTime;

	public static HospitalSchedule create(Hospital hospital, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
		HospitalSchedule schedule = new HospitalSchedule();
		schedule.hospital = hospital;
		schedule.dayOfWeek = dayOfWeek;
		schedule.openTime = openTime;
		schedule.closeTime = closeTime;
		return schedule;
	}

	public boolean is24Hours() {
		return LocalTime.MIDNIGHT.equals(openTime) && LocalTime.of(23, 59).equals(closeTime);
	}
}
