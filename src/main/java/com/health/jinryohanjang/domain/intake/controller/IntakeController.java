package com.health.jinryohanjang.domain.intake.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.SuccessStatus;
import com.health.jinryohanjang.domain.auth.security.AuthCookies;
import com.health.jinryohanjang.domain.auth.security.LoginMember;
import com.health.jinryohanjang.domain.intake.dto.AnswerRequest;
import com.health.jinryohanjang.domain.intake.dto.IntakeCancelResponse;
import com.health.jinryohanjang.domain.intake.dto.IntakeResponse;
import com.health.jinryohanjang.domain.intake.dto.IntakeStartRequest;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.intake.service.IntakeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * 문진(질문·답변) API를 제공한다.
 */
@Tag(name = "intake", description = "문진(질문/답변), 위험 신호 판정")
@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
@SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN)
@RestController
@RequestMapping("/api/intakes")
@RequiredArgsConstructor
public class IntakeController {

	public static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
	public static final String INVALID_ID_MESSAGE = "문진 ID 형식이 올바르지 않아요";

	private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

	private final IntakeService intakeService;
	private final AuthCookies authCookies;

	private static final String COMMON_ERRORS = """
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `INTAKE_403` | guestKey가 없거나 이 문진의 것이 아님 → S01 |
		| `INTAKE_404` | 문진이 없거나 보관 시간(1시간)이 지나 삭제됨 → S01 |
		""";

	@Operation(summary = "문진 시작 (S01, S03, S04)", description = """
		말·글·버튼으로 문진을 시작한다. guestKey 쿠키가 없으면 이 응답에서 발급한다 (회원도 발급). 말로 시작해서 STT를 먼저 불렀으면 그때 받은 guestKey를 그대로 쓴다. 첫 입력이 위험 신호면 status = EMERGENCY다.

		BUTTON(S04)으로 시작하면 bodyPart·onset이 필수이고 바로 BASIC_INFO 단계로 간다. VOICE·TEXT면 text가 필수다.

		| code | 설명 |
		| --- | --- |
		| `INTAKE_201` | 문진 시작 성공 |
		| `INTAKE_400` | 입력 방식과 필드 불일치 (BUTTON인데 bodyPart 없음, VOICE·TEXT인데 text 없음) |
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `INTAKE_422` | 증상을 알아들을 수 없음 (빈 말, 증상과 무관한 내용) |
		| `INTAKE_429` | 호출 제한 초과 (IP당 분당 10개). Retry-After 헤더만큼 기다렸다 다시 |
		| `INTAKE_503` | AI 호출 실패. 잠시 후 다시 시도 |
		""")
	@PostMapping
	public ResponseEntity<ApiResponse<IntakeResponse>> start(
		@Valid @RequestBody IntakeStartRequest request,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId,
		HttpServletRequest httpRequest) {
		IntakeService.StartResult result = intakeService.start(request, guestKey, clientIp(httpRequest));
		return ApiResponse.success(SuccessStatus.INTAKE_START_SUCCESS, result.response(), guestKeyCookie(result.guestKey()));
	}

	@Operation(summary = "문진 상태 조회 (S03)", description = """
		문진 상태와 현재 질문을 돌려준다. 새로고침·재접속 때 이어 하기에 쓴다.

		| code | 설명 |
		| --- | --- |
		| `INTAKE_200` | 조회 성공 |
		| `INTAKE_400` | intakeId 형식 오류 |
		""" + COMMON_ERRORS)
	@GetMapping("/{intakeId}")
	public ResponseEntity<ApiResponse<IntakeResponse>> get(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = UUID_PATTERN, message = INVALID_ID_MESSAGE) String intakeId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.INTAKE_GET_SUCCESS, intakeService.get(intakeId, guestKey),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "답변 전송 (S03, S04a)", description = """
		답변을 받아 다음 질문, 응급 전환, 완료 중 하나로 응답한다.
		- IN_PROGRESS: 다음 질문. 못 알아들으면 같은 질문과 reask = true
		- EMERGENCY: emergency를 채움 → E01
		- COMPLETED: department와 summaryId를 채우고, 서버가 요약지 생성을 바로 시작 → S05a·S05

		answerType별 답: YES_NO_UNKNOWN은 BUTTON이면 YES/NO/UNKNOWN, 말·글이면 AI가 해석한다. SINGLE_CHOICE는 BUTTON이면 option code, 말·글이면 AI가 options 중 하나로 맞춘다. FREE_TEXT는 말·글만 된다.

		| code | 설명 |
		| --- | --- |
		| `INTAKE_200` | 답변 처리 성공 |
		| `INTAKE_400` | inputMode와 answer 형식 불일치 (BUTTON인데 정해진 값이 아님, FREE_TEXT인데 BUTTON) |
		| `INTAKE_409` | result = null: 현재 질문이 아님 (중복 전송) → GET으로 다시 조회 / result.status 있음: 이미 완료·응급·취소된 문진 → 그 화면으로 |
		| `INTAKE_429` | 호출 제한 초과 (guestKey당 분당 30회) |
		| `INTAKE_503` | AI 호출 실패. 답변은 저장하지 않아서 같은 questionId로 다시 보내면 된다 |
		""" + COMMON_ERRORS)
	@PostMapping("/{intakeId}/answers")
	public ResponseEntity<ApiResponse<IntakeResponse>> answer(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = UUID_PATTERN, message = INVALID_ID_MESSAGE) String intakeId,
		@Valid @RequestBody AnswerRequest request,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.INTAKE_ANSWER_SUCCESS, intakeService.answer(intakeId, guestKey, request),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "이전 질문 (S03)", description = """
		직전 답변을 지우고 그 질문을 다시 묻는다.

		| code | 설명 |
		| --- | --- |
		| `INTAKE_200` | 이전 질문으로 이동 |
		| `INTAKE_400` | 첫 질문이라 돌아갈 수 없음 → 이전 버튼 숨김 |
		| `INTAKE_409` | 이미 완료·응급 전환된 문진 |
		""" + COMMON_ERRORS)
	@PostMapping("/{intakeId}/back")
	public ResponseEntity<ApiResponse<IntakeResponse>> back(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = UUID_PATTERN, message = INVALID_ID_MESSAGE) String intakeId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.INTAKE_BACK_SUCCESS, intakeService.back(intakeId, guestKey),
			guestKeyCookie(guestKey));
	}

	@Operation(summary = "문진 취소 (S06a)", description = """
		Redis의 문진·요약지를 지우고, 요약지 생성 중이면 중단한다. 처음(S01)으로 돌아갈 때 쓴다.

		| code | 설명 |
		| --- | --- |
		| `INTAKE_200` | 취소 완료 |
		| `AUTH_401` | accessToken이 있는데 만료·위조됨 → refresh 후 다시 (refresh도 401이면 비로그인으로 계속) |
		| `INTAKE_403` | guestKey가 없거나 이 문진의 것이 아님 → S01 |
		| `INTAKE_404` | 문진이 없거나 이미 취소·만료·보관됨 → S01 |
		""")
	@PostMapping("/{intakeId}/cancel")
	public ResponseEntity<ApiResponse<IntakeCancelResponse>> cancel(
		@Parameter(description = "문진 ID (UUID)", example = "3f2a9c1e-7b4d-4e8a-9c21-5d6f0a1b2c3d")
		@PathVariable @Pattern(regexp = UUID_PATTERN, message = INVALID_ID_MESSAGE) String intakeId,
		@Parameter(hidden = true) @CookieValue(name = AuthCookies.GUEST_KEY, required = false) String guestKey,
		@Parameter(hidden = true) @LoginMember(required = false) Long memberId) {
		return ApiResponse.success(SuccessStatus.INTAKE_CANCEL_SUCCESS, intakeService.cancel(intakeId, guestKey),
			new HttpHeaders());
	}

	/**
	 * guestKey 쿠키를 다시 1시간으로 늘린다. 문진 TTL과 같이 간다.
	 */
	private HttpHeaders guestKeyCookie(String guestKey) {
		HttpHeaders headers = new HttpHeaders();
		if (guestKey != null) {
			headers.add(HttpHeaders.SET_COOKIE, authCookies.create(AuthCookies.GUEST_KEY, guestKey, IntakeStore.TTL).toString());
		}
		return headers;
	}

	/**
	 * 프론트(Cloudflare) 프록시를 거치므로 CF-Connecting-IP를 먼저 본다.
	 */
	private String clientIp(HttpServletRequest request) {
		String ip = request.getHeader(CF_CONNECTING_IP);
		return ip != null && !ip.isBlank() ? ip.trim() : request.getRemoteAddr();
	}
}
