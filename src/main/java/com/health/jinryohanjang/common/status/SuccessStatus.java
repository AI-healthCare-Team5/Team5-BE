package com.health.jinryohanjang.common.status;

import org.springframework.http.HttpStatus;

import com.health.jinryohanjang.common.base.BaseStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성공 응답 목록. API를 만들 때 도메인별로 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum SuccessStatus implements BaseStatus {

	// intake
	INTAKE_ANSWER_SUCCESS("INTAKE_200", HttpStatus.OK, "답변 처리 성공"),

	// consent
	CONSENT_ALREADY_AGREED("CONSENT_200", HttpStatus.OK, "이미 동의했어요."),
	CONSENT_AGREED("CONSENT_201", HttpStatus.CREATED, "동의 성공");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;
}
