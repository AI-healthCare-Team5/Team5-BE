package com.health.jinryohanjang.domain.summary.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.auth.security.AuthCookies;
import com.health.jinryohanjang.domain.auth.security.LoginMember;
import com.health.jinryohanjang.domain.intake.controller.IntakeController;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.summary.dto.EmergencyCardResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryConfirmResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryRetryResponse;
import com.health.jinryohanjang.domain.summary.dto.SummarySaveResponse;
import com.health.jinryohanjang.domain.summary.dto.SummaryUpdateRequest;
import com.health.jinryohanjang.domain.summary.service.EmergencyCardService;
import com.health.jinryohanjang.domain.summary.service.RecordService;
import com.health.jinryohanjang.domain.summary.service.SummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * 요약지와 응급 카드 API를 제공한다.
 */
@Tag(name = "summary", description = "요약지, 응급 카드, 내 기록")
@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequiredArgsConstructor
public class SummaryController {

	private static final String INVALID_SUMMARY_ID_MESSAGE = "요약지 ID 형식이 올바르지 않아요";

	private static final String SUMMARY_ERRORS = """
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `SUMMARY_403` | guestKey가 없거나 이 요약지의 것이 아님 → S01 |
		| `SUMMARY_404` | 요약지가 없거나 취소·만료로 삭제됨 → S01 |
		""";

	private final SummaryService summaryService;
	private final EmergencyCardService emergencyCardService;
	private final RecordService recordService;
	private final AuthCookies authCookies;

	@Operation(summary = "요약지 다시 만들기 (S06a)", description = """
		생성에 실패(FAILED)한 요약지를 다시 만든다. 정상 흐름에서는 문진이 COMPLETED 될 때 서버가 자동으로 시작하므로 실패했을 때만 쓴다. summaryId는 그대로라 요약지 조회로 폴링하면 된다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_202` | 다시 생성 시작 |
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `SUMMARY_403` | guestKey가 없거나 이 문진의 것이 아님 → S01 |
		| `SUMMARY_404` | 문진이 없거나 보관 시간(1시간)이 지나 삭제됨 → S01 |
		| `SUMMARY_409` | 완료되지 않은 문진, 또는 생성 중이거나 이미 완료된 요약지 → 요약지 조회 계속 |
		""")
	@PostMapping("/api/intakes/{intakeId}/summary")
	public ResponseEntity<ApiResponse<SummaryRetryResponse>> retry(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = IntakeController.INVALID_ID_MESSAGE)
		String intakeId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.SUMMARY_RETRY_SUCCESS, summaryService.retry(intakeId, guestKey),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "요약지 조회 (S06a, S06)", description = """
		생성 진행 상태와 요약지를 돌려준다. S06a는 1초 간격으로 폴링한다. 생성 시작 후 30초 안에 끝나지 않으면 서버가 FAILED로 바꾼다.
		- GENERATING: steps만 채우고 summary = null
		- DONE: summary를 채움
		- FAILED: 요약지 다시 만들기 안내

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 조회 성공 |
		""" + SUMMARY_ERRORS)
	@GetMapping("/api/summaries/{summaryId}")
	public ResponseEntity<ApiResponse<SummaryResponse>> get(
		@Parameter(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = INVALID_SUMMARY_ID_MESSAGE)
		String summaryId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.SUMMARY_GET_SUCCESS, summaryService.get(summaryId, guestKey),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "요약지 수정 (S06)", description = """
		"고칠 곳이 있어요". 보낸 필드만 바꾸고 안 보낸 필드는 그대로 둔다. null을 보내면 "확인 못 함"(또는 지움)이다. ageGroup·gender·mainSymptom은 null로 바꿀 수 없다. 수정하면 isConfirmed는 false로 돌아간다. 응답 모양은 요약지 조회와 같다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 수정 성공 |
		| `SUMMARY_400` | enum·길이 오류, null 불가 필드에 null, 없는 order |
		| `SUMMARY_409` | 아직 생성 중이거나 실패한 요약지 (result = null) → S06a |
		""" + SUMMARY_ERRORS)
	@PatchMapping("/api/summaries/{summaryId}")
	public ResponseEntity<ApiResponse<SummaryResponse>> update(
		@Parameter(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = INVALID_SUMMARY_ID_MESSAGE)
		String summaryId,
		@RequestBody SummaryUpdateRequest request,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.SUMMARY_UPDATE_SUCCESS,
			summaryService.update(summaryId, guestKey, request), guestKeyCookie(guestKey));
	}

	@Operation(summary = "요약지 확인 (S06)", description = """
		"내용이 맞아요". isConfirmed만 true로 바꾼다. 보관 전 필수 단계는 아니고, 보관하면 그대로 저장된다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 확인 처리 |
		| `SUMMARY_409` | 아직 생성 중이거나 실패한 요약지 (result = null) → S06a |
		""" + SUMMARY_ERRORS)
	@PostMapping("/api/summaries/{summaryId}/confirm")
	public ResponseEntity<ApiResponse<SummaryConfirmResponse>> confirm(
		@Parameter(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = INVALID_SUMMARY_ID_MESSAGE)
		String summaryId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.SUMMARY_CONFIRM_SUCCESS, summaryService.confirm(summaryId, guestKey),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "내 기록에 보관 (S06)", description = """
		로그인과 기록 저장 동의가 필요하다. 요약지 본인 확인은 guestKey로 한다 ("로그인하고 보관하기": 카카오 로그인 → C02 → 보관). 요약지를 DB로 복사하고 Redis의 문진·요약지는 바로 지운다. 이미 보관한 요약지는 Redis가 지워졌어도 SUMMARY_409와 medicalRecordId를 준다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_201` | 보관 성공 |
		| `AUTH_401` | 토큰 없음·만료·탈퇴한 회원 → refresh 한 번 시도, 그래도 401이면 L01 |
		| `CONSENT_403` | 기록 저장 동의(RECORD)가 없음 → C02 |
		| `SUMMARY_403` | guestKey가 없거나 이 요약지의 것이 아님 |
		| `SUMMARY_404` | 요약지가 없거나 취소·만료로 삭제됨 → S01 |
		| `SUMMARY_409` | result = null: 아직 생성 중이거나 실패 → S06a / result.medicalRecordId 있음: 이미 보관 → H02 |
		""")
	@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
	@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
	@PostMapping("/api/summaries/{summaryId}/save")
	public ResponseEntity<ApiResponse<SummarySaveResponse>> save(
		@Parameter(description = "요약지 ID (UUID)", example = "8b1d2e3f-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = INVALID_SUMMARY_ID_MESSAGE)
		String summaryId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember Long memberId) {
		return ApiResponse.success(SuccessStatus.SUMMARY_SAVE_SUCCESS, recordService.save(memberId, summaryId, guestKey));
	}

	@Operation(summary = "응급 카드 조회 (E02)", description = """
		응급 전환된 문진의 응급 카드를 돌려준다. 못 물은 항목은 null("확인 못 했어요")이다. 복용약·앓는 병·알레르기는 AI 없이 사용자가 한 말을 그대로 한 항목으로 준다. 보호자는 로그인했고 등록된 경우에만 전체 번호로 주고, 아니면 null이다. 현재 위치 주소는 프론트가 카카오맵 SDK로 변환한다.

		| code | 설명 |
		| --- | --- |
		| `SUMMARY_200` | 조회 성공 |
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `SUMMARY_403` | guestKey가 없거나 이 문진의 것이 아님 → S01 |
		| `SUMMARY_404` | 문진이 없거나 보관 시간(1시간)이 지나 삭제됨 → S01 |
		| `SUMMARY_409` | 응급 전환(EMERGENCY)되지 않은 문진 → 문진 화면으로 |
		""")
	@GetMapping("/api/intakes/{intakeId}/emergency-card")
	public ResponseEntity<ApiResponse<EmergencyCardResponse>> getEmergencyCard(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = IntakeController.UUID_PATTERN, message = IntakeController.INVALID_ID_MESSAGE)
		String intakeId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.EMERGENCY_CARD_SUCCESS,
			emergencyCardService.get(intakeId, guestKey, memberId), guestKeyCookie(guestKey));
	}

	/**
	 * guestKey 쿠키를 다시 1시간으로 늘린다. 문진·요약지 TTL과 같이 간다.
	 */
	private HttpHeaders guestKeyCookie(String guestKey) {
		HttpHeaders headers = new HttpHeaders();
		if (guestKey != null) {
			headers.add(HttpHeaders.SET_COOKIE, authCookies.create(AuthCookies.GUEST_KEY, guestKey, IntakeStore.TTL).toString());
		}
		return headers;
	}
}
