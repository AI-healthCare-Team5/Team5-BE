package com.health.jinryohanjang.global.error;

import org.springframework.http.HttpStatus;

import com.health.jinryohanjang.global.common.response.Domain;

import lombok.Getter;

/**
 * 서비스에서 실패 응답을 내려야 할 때 던지는 예외
 * 응답 code는 domain과 status로 만든다 ({도메인}_{HTTP 상태}).
 * 401은 어느 API든 AUTH, 보관 시 기록 저장 동의가 없으면 CONSENT를 domain으로 쓴다.
 * 실패 result는 기본 null이고, 같은 code 안에서 이유를 구분해야 할 때만 result를 담는다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final Domain domain;
	private final HttpStatus status;
	private final Object result;

	public BusinessException(Domain domain, HttpStatus status, String message) {
		this(domain, status, message, null);
	}

	public BusinessException(Domain domain, HttpStatus status, String message, Object result) {
		super(message);
		this.domain = domain;
		this.status = status;
		this.result = result;
	}
}
