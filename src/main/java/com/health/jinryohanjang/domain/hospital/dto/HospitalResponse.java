package com.health.jinryohanjang.domain.hospital.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 병원 검색·응급실·보건소가 함께 쓰는 병원 한 곳의 정보다.
 */
public record HospitalResponse(
	@Schema(description = "병원 ID", example = "101")
	Long hospitalId,

	@Schema(description = "병원 이름", example = "OO정형외과의원")
	String name,

	@Schema(description = "심평원 종별코드 (01 상급종합, 11 종합병원, 21 병원, 28 요양병원, 31 의원, 71 보건소 등)", example = "31")
	String hospitalType,

	@Schema(description = "종별 이름", example = "의원")
	String hospitalTypeName,

	@Schema(description = "진료과 목록")
	List<Department> departments,

	@Schema(description = "현재 위치(또는 지역 중심)에서의 거리 km, 소수 1자리", example = "1.2")
	double distanceKm,

	@Schema(description = "위도 (WGS84)", example = "37.6971234")
	double latitude,

	@Schema(description = "경도 (WGS84)", example = "127.8887654")
	double longitude,

	@Schema(description = "전화번호", example = "033-123-4567", nullable = true)
	String phoneNumber,

	@Schema(description = "주소", example = "강원특별자치도 홍천군 홍천읍 신장대로 00")
	String address,

	@Schema(description = "응급실이 있는지", example = "false")
	boolean hasEmergencyRoom,

	@Schema(description = "오늘 진료 시간. 진료 시간 정보가 아예 없으면 null", nullable = true)
	Today today
) {

	public record Department(
		@Schema(description = "심평원 진료과목코드", example = "05")
		String departmentCode,

		@Schema(description = "진료과 이름", example = "정형외과")
		String departmentName
	) {
	}

	public record Today(
		@Schema(description = "오늘 진료하는지. 그 요일 진료 시간이 없으면 휴진(false)", example = "true")
		boolean isOpen,

		@Schema(description = "24시간 진료인지. 응급실은 항상 true (00:00~23:59)", example = "false")
		boolean is24Hours,

		@Schema(description = "진료 시작 시각 (HH:mm)", example = "09:00", nullable = true)
		String openTime,

		@Schema(description = "진료 종료 시각 (HH:mm)", example = "18:00", nullable = true)
		String closeTime,

		@Schema(description = "휴진일 때 다음 진료 시작 시각. 진료 중이면 null",
			example = "2026-10-31T09:00:00+09:00", nullable = true)
		OffsetDateTime nextOpenAt
	) {
	}
}
