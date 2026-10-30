package com.health.jinryohanjang.global.common.response;

import org.springframework.http.HttpStatus;

/**
 * 응답 code의 도메인 부분
 * code는 {도메인}_{HTTP 상태} 형식이다 (예: INTAKE_200, SUMMARY_409).
 */
public enum Domain {
	AUTH,
	MEMBER,
	CONSENT,
	GUARDIAN,
	INTAKE,
	DEPARTMENT,
	HOSPITAL,
	SUMMARY,
	STT;

	public String code(HttpStatus status) {
		return name() + "_" + status.value();
	}
}
