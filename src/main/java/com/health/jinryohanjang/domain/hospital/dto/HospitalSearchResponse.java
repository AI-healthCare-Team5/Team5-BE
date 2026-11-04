package com.health.jinryohanjang.domain.hospital.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 병원 검색과 응급실 검색이 함께 쓰는 응답이다.
 */
public record HospitalSearchResponse(
	@Schema(description = "검색한 반경 km", example = "10")
	int radiusKm,

	@Schema(description = "마지막 공공데이터 동기화 시각 (화면 \"공공데이터 기준 · 갱신일\")", example = "2026-10-30T04:00:00+09:00")
	OffsetDateTime dataUpdatedAt,

	@Schema(description = "반경 안 전체 병원 수 (limit과 무관)", example = "5")
	long totalCount,

	@Schema(description = "가까운 순 병원 목록. 0건이면 []")
	List<HospitalResponse> hospitals
) {
}
