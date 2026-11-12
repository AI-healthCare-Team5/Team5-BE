package com.health.jinryohanjang.domain.hospital.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegionListResponse(
	@Schema(description = "서비스 지역 시·군(기본 춘천시·원주시). 이름순(가나다) 정렬")
	List<Region> regions
) {

	public record Region(
		@Schema(description = "지역 코드. 병원 검색의 regionCode로 쓴다.", example = "51720")
		String regionCode,

		@Schema(description = "지역 이름", example = "홍천군")
		String regionName,

		@Schema(description = "중심 위도", example = "37.697")
		double centerLatitude,

		@Schema(description = "중심 경도", example = "127.8888")
		double centerLongitude
	) {
	}
}
