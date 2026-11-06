package com.health.jinryohanjang.domain.hospital.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.hospital.dto.HealthCenterResponse;
import com.health.jinryohanjang.domain.hospital.dto.HospitalSearchResponse;
import com.health.jinryohanjang.domain.hospital.service.HospitalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 병원·응급실·보건소 검색 API를 제공한다. 토큰 없이 호출한다.
 */
@Tag(name = "hospital", description = "병원 · 응급실 · 보건소 검색, 지역 목록")
@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalController {

	private final HospitalService hospitalService;

	private static final String LATITUDE_MESSAGE = "위도는 -90~90 사이여야 해요";
	private static final String LONGITUDE_MESSAGE = "경도는 -180~180 사이여야 해요";

	@Operation(summary = "병원 검색 (S05, S05c, S05d)", description = """
		진료과·위치(또는 지역)·반경으로 가까운 순 병원을 찾는다. 위치(latitude·longitude)와 regionCode 중 하나는 있어야 하고, 위치가 없으면 그 지역 중심 좌표로 찾는다. 병원 데이터는 매일 04:00 공공데이터를 동기화한 DB에서만 조회한다.

		| code | 설명 |
		| --- | --- |
		| `HOSPITAL_200` | 검색 성공. 0건이면 hospitals = [] → S05c |
		| `HOSPITAL_400` | 위치와 지역이 모두 없음: errors[0].field = "location" → S05b |
		| `HOSPITAL_400` | 좌표·radius·limit 범위 오류, 없는 진료과·지역 코드: field = 해당 파라미터 이름 |
		| `HOSPITAL_500` | 조회 실패 → S05d |
		""")
	@GetMapping
	public ResponseEntity<ApiResponse<HospitalSearchResponse>> search(
		@Parameter(description = "진료과 코드 (심평원 진료과목코드). 없으면 전체", example = "05")
		@RequestParam(required = false) String departmentCode,
		@Parameter(description = "현재 위치 위도 (WGS84)", example = "37.6971234")
		@RequestParam(required = false)
		@DecimalMin(value = "-90", message = LATITUDE_MESSAGE) @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
		Double latitude,
		@Parameter(description = "현재 위치 경도 (WGS84). latitude와 함께 보낸다.", example = "127.8887654")
		@RequestParam(required = false)
		@DecimalMin(value = "-180", message = LONGITUDE_MESSAGE) @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
		Double longitude,
		@Parameter(description = "지역 코드 (S05b). 위치가 없을 때 쓴다.", example = "51720")
		@RequestParam(required = false) String regionCode,
		@Parameter(description = "검색 반경 km. 기본 10, 최대 30 (S05c \"30km까지 넓혀서 찾기\")", example = "10")
		@RequestParam(defaultValue = "10")
		@Min(value = 1, message = "반경은 1km 이상이어야 해요") @Max(value = 30, message = "30km까지 검색할 수 있어요")
		int radius,
		@Parameter(description = "가까운 순 몇 곳까지. 기본 3, 최대 20", example = "3")
		@RequestParam(defaultValue = "3")
		@Min(value = 1, message = "1곳 이상이어야 해요") @Max(value = 20, message = "20곳까지 볼 수 있어요")
		int limit) {
		return ApiResponse.success(SuccessStatus.HOSPITAL_SEARCH_SUCCESS,
			hospitalService.search(departmentCode, latitude, longitude, regionCode, radius, limit));
	}

	@Operation(summary = "가까운 응급실 (E01)", description = """
		응급실이 있는 병원만 가까운 순으로 찾는다. 응급 전환은 위치 확인(S05a) 전에 나올 수 있어서 regionCode로도 찾는다. 응답 모양은 병원 검색과 같고, 응급실은 항상 is24Hours = true다.

		| code | 설명 |
		| --- | --- |
		| `HOSPITAL_200` | 조회 성공. 0건이면 [] (E01은 119 안내만 표시) |
		| `HOSPITAL_400` | 위치와 지역이 모두 없음: field = "location" → 119 안내만 |
		| `HOSPITAL_400` | 좌표·radius 범위 오류, 없는 지역 코드 |
		| `HOSPITAL_500` | 서버 내부 에러 |
		""")
	@GetMapping("/emergency-rooms")
	public ResponseEntity<ApiResponse<HospitalSearchResponse>> searchEmergencyRooms(
		@Parameter(description = "현재 위치 위도", example = "37.6971234")
		@RequestParam(required = false)
		@DecimalMin(value = "-90", message = LATITUDE_MESSAGE) @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
		Double latitude,
		@Parameter(description = "현재 위치 경도", example = "127.8887654")
		@RequestParam(required = false)
		@DecimalMin(value = "-180", message = LONGITUDE_MESSAGE) @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
		Double longitude,
		@Parameter(description = "지역 코드", example = "51720")
		@RequestParam(required = false) String regionCode,
		@Parameter(description = "검색 반경 km. 기본 30, 최대 50", example = "30")
		@RequestParam(defaultValue = "30")
		@Min(value = 1, message = "반경은 1km 이상이어야 해요") @Max(value = 50, message = "50km까지 검색할 수 있어요")
		int radius,
		@Parameter(description = "몇 곳까지. 기본 2 (E01), 최대 10", example = "2")
		@RequestParam(defaultValue = "2")
		@Min(value = 1, message = "1곳 이상이어야 해요") @Max(value = 10, message = "10곳까지 볼 수 있어요")
		int limit) {
		return ApiResponse.success(SuccessStatus.EMERGENCY_ROOM_SUCCESS,
			hospitalService.searchEmergencyRooms(latitude, longitude, regionCode, radius, limit));
	}

	@Operation(summary = "가까운 보건소 (S05c)", description = """
		보건소 계열(종별 71 보건소, 72 보건지소, 73 보건진료소, 75 보건의료원) 중 반경 제한 없이 가장 가까운 1곳을 돌려준다. S05c "가까운 보건소에 전화하기"에 쓴다.

		| code | 설명 |
		| --- | --- |
		| `HOSPITAL_200` | 조회 성공. 없으면 hospital = null |
		| `HOSPITAL_400` | 위치와 지역이 모두 없음: field = "location" |
		| `HOSPITAL_400` | 좌표 범위 오류, 없는 지역 코드 |
		| `HOSPITAL_500` | 서버 내부 에러 |
		""")
	@GetMapping("/health-centers/nearest")
	public ResponseEntity<ApiResponse<HealthCenterResponse>> findNearestHealthCenter(
		@Parameter(description = "현재 위치 위도", example = "37.6971234")
		@RequestParam(required = false)
		@DecimalMin(value = "-90", message = LATITUDE_MESSAGE) @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
		Double latitude,
		@Parameter(description = "현재 위치 경도", example = "127.8887654")
		@RequestParam(required = false)
		@DecimalMin(value = "-180", message = LONGITUDE_MESSAGE) @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
		Double longitude,
		@Parameter(description = "지역 코드. 위치가 없을 때 쓴다.", example = "51720")
		@RequestParam(required = false) String regionCode) {
		return ApiResponse.success(SuccessStatus.HEALTH_CENTER_SUCCESS,
			hospitalService.findNearestHealthCenter(latitude, longitude, regionCode));
	}
}
