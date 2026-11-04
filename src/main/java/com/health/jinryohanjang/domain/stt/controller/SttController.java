package com.health.jinryohanjang.domain.stt.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.health.jinryohanjang.common.config.SwaggerConfig;
import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.stt.dto.SttResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 음성 인식 API를 제공한다.
 */
@Tag(name = "stt", description = "음성 인식")
@SecurityRequirement(name = SwaggerConfig.GUEST_KEY)
@RestController
public class SttController {

	@Operation(summary = "음성 인식 (S03)", description = """
		녹음 파일을 글자로 바꾼다. 받은 글자를 문진 시작이나 답변에 inputMode = VOICE로 보낸다 (답변 API는 음성을 직접 받지 않는다). 음성 파일은 저장하지 않는다.

		guestKey가 없으면 이 응답에서 새로 발급한다 (말로 시작하면 문진 시작보다 STT가 먼저라서). accessToken은 보지 않아서 만료돼도 401이 나지 않는다.

		| code | 설명 |
		| --- | --- |
		| `STT_200` | 인식 성공 |
		| `STT_400` | audio 파일 없음 → 다시 녹음 |
		| `STT_413` | 10MB 또는 60초 초과 → 짧게 다시 말하기 |
		| `STT_415` | 지원하지 않는 녹음 형식 → 글로 입력 |
		| `STT_422` | 말소리를 알아들을 수 없음 (무음·잡음) → "다시 말씀해 주세요" |
		| `STT_429` | 호출 제한 초과 (IP당 분당 10회, guestKey당 시간당 60회) → 글로 입력 |
		| `STT_503` | STT 서비스 호출 실패 → 글로 입력 |
		""")
	@PostMapping(value = "/api/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<SttResponse>> recognize(
		@Parameter(description = "녹음 파일. webm(크롬)·mp4/m4a(사파리). 최대 10MB · 60초")
		@RequestPart MultipartFile audio) {
		throw new GeneralException(ErrorStatus.NOT_IMPLEMENTED);
	}
}
