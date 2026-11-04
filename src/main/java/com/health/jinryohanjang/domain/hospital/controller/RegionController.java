package com.health.jinryohanjang.domain.hospital.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.hospital.dto.RegionListResponse;
import com.health.jinryohanjang.domain.hospital.service.HospitalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/**
 * 병원 검색에 쓰는 시·군 목록 API를 제공한다.
 */
@Tag(name = "hospital", description = "병원 · 응급실 · 보건소 검색, 지역 목록")
@RestController
@RequiredArgsConstructor
public class RegionController {

	private final HospitalService hospitalService;

	@Operation(summary = "시·군 목록 (S05b)", description = """
		강원 18개 시·군을 이름순으로 돌려준다. regionCode가 병원 검색 기준이라 서버에서 내려주고, 프론트는 처음 한 번 받아서 캐시하면 된다.

		| code | 설명 |
		| --- | --- |
		| `HOSPITAL_200` | 조회 성공 |
		| `HOSPITAL_500` | 서버 내부 에러 |
		""")
	@GetMapping("/api/regions")
	public ResponseEntity<ApiResponse<RegionListResponse>> getRegions() {
		return ApiResponse.success(SuccessStatus.REGION_LIST_SUCCESS, hospitalService.getRegions());
	}
}
