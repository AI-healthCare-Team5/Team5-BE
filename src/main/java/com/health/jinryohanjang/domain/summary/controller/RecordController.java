package com.health.jinryohanjang.domain.summary.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.summary.dto.RecordDeleteResponse;
import com.health.jinryohanjang.domain.summary.dto.RecordDetailResponse;
import com.health.jinryohanjang.domain.summary.dto.RecordListResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 내 기록(보관한 요약지) API를 제공한다.
 */
@Tag(name = "summary", description = "요약지, 응급 카드, 내 기록")
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/members/me/summaries")
public class RecordController {

	private static final String LOGIN_ERROR = """
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		""";

	@Operation(summary = "내 기록 목록 (H01)", description = """
		보관한 기록을 최근 순으로 돌려준다. subtitle(H01 두 번째 줄)은 서버가 onsetDetail과 첫 번째 증상별 항목으로 만든다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 조회 성공. 기록이 없으면 records = [] |
		""" + LOGIN_ERROR)
	@GetMapping
	public ResponseEntity<ApiResponse<RecordListResponse>> getRecords(
		@Parameter(description = "페이지 번호 (0부터)", example = "0")
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0부터 시작해요") int page,
		@Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20")
		@RequestParam(defaultValue = "20")
		@Min(value = 1, message = "1개 이상이어야 해요") @Max(value = 50, message = "50개까지 볼 수 있어요") int size) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}

	@Operation(summary = "기록 상세 (H02)", description = """
		요약지 조회와 같은 summary 모양으로 돌려준다. null(확인 못 함)과 [](없음)이 보관할 때 그대로 유지된다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 조회 성공 |
		| `SUMMARY_403` | 다른 회원의 기록 → H01 |
		| `SUMMARY_404` | 기록이 없거나 삭제됨 → H01 |
		""" + LOGIN_ERROR)
	@GetMapping("/{medicalRecordId}")
	public ResponseEntity<ApiResponse<RecordDetailResponse>> getRecord(
		@Parameter(description = "진료 기록 ID", example = "12") @PathVariable Long medicalRecordId) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}

	@Operation(summary = "기록 삭제 (H01, H02)", description = """
		deleted_at을 기록해 목록·상세에서 바로 사라지고, 30일 뒤 배치가 완전히 지운다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 삭제 성공 |
		| `SUMMARY_403` | 다른 회원의 기록 |
		| `SUMMARY_404` | 기록이 없거나 이미 삭제됨 |
		""" + LOGIN_ERROR)
	@DeleteMapping("/{medicalRecordId}")
	public ResponseEntity<ApiResponse<RecordDeleteResponse>> deleteRecord(
		@Parameter(description = "진료 기록 ID", example = "12") @PathVariable Long medicalRecordId) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}
}
