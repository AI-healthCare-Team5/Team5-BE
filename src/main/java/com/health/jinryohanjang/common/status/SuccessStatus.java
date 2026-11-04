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

	// auth
	AUTHORIZE_URL_SUCCESS("AUTH_200", HttpStatus.OK, "카카오 로그인 URL 조회 성공"),
	LOGIN_SUCCESS("AUTH_200", HttpStatus.OK, "로그인 성공"),
	REFRESH_SUCCESS("AUTH_200", HttpStatus.OK, "토큰 재발급 성공"),
	LOGOUT_SUCCESS("AUTH_200", HttpStatus.OK, "로그아웃 성공"),

	// hospital
	HOSPITAL_SEARCH_SUCCESS("HOSPITAL_200", HttpStatus.OK, "병원 검색 성공"),
	EMERGENCY_ROOM_SUCCESS("HOSPITAL_200", HttpStatus.OK, "응급실 조회 성공"),
	HEALTH_CENTER_SUCCESS("HOSPITAL_200", HttpStatus.OK, "보건소 조회 성공"),
	REGION_LIST_SUCCESS("HOSPITAL_200", HttpStatus.OK, "지역 목록 조회 성공"),

	// intake
	INTAKE_ANSWER_SUCCESS("INTAKE_200", HttpStatus.OK, "답변 처리 성공"),

	// consent
	CONSENT_ALREADY_AGREED("CONSENT_200", HttpStatus.OK, "이미 동의했어요."),
	CONSENT_AGREED("CONSENT_201", HttpStatus.CREATED, "동의 성공");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;
}
