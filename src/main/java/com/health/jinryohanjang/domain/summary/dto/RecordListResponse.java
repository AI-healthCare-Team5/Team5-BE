package com.health.jinryohanjang.domain.summary.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecordListResponse(
	@Schema(description = "페이지 번호 (0부터)", example = "0")
	int page,

	@Schema(description = "페이지 크기", example = "20")
	int size,

	@Schema(description = "전체 기록 수", example = "1")
	long totalCount,

	@Schema(description = "다음 페이지가 있는지", example = "false")
	boolean hasNext,

	@Schema(description = "최근 순 기록 목록. 없으면 []")
	List<RecordItem> records
) {

	public record RecordItem(
		@Schema(description = "진료 기록 ID", example = "12")
		Long medicalRecordId,

		@Schema(description = "보관한 시각", example = "2026-10-16T14:25:00+09:00")
		OffsetDateTime createdAt,

		@Schema(description = "추천 진료과 이름", example = "정형외과")
		String departmentName,

		@Schema(description = "주된 증상", example = "무릎 통증")
		String mainSymptom,

		@Schema(description = "H01 두 번째 줄. onsetDetail + 첫 번째 증상별 항목", example = "3일 전부터 · 붓기 있음")
		String subtitle
	) {
	}
}
