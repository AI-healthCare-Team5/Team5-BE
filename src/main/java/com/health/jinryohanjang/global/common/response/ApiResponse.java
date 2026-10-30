package com.health.jinryohanjang.global.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 모든 API의 공통 응답 형식
 * - code : {도메인}_{HTTP 상태} (예: INTAKE_200, INTAKE_201, SUMMARY_409)
 * - 실패 result는 기본 null
 * HTTP 상태와 code가 어긋나지 않도록 ResponseEntity로 함께 만든다.
 */
public record ApiResponse<T>(
	boolean isSuccess,
	String code,
	String message,
	T result
) {

	public static <T> ResponseEntity<ApiResponse<T>> ok(Domain domain, String message, T result) {
		return of(domain, HttpStatus.OK, message, result);
	}

	public static <T> ResponseEntity<ApiResponse<T>> created(Domain domain, String message, T result) {
		return of(domain, HttpStatus.CREATED, message, result);
	}

	public static <T> ResponseEntity<ApiResponse<T>> accepted(Domain domain, String message, T result) {
		return of(domain, HttpStatus.ACCEPTED, message, result);
	}

	public static <T> ResponseEntity<ApiResponse<T>> of(Domain domain, HttpStatus status, String message, T result) {
		ApiResponse<T> body = new ApiResponse<>(!status.isError(), domain.code(status), message, result);
		return ResponseEntity.status(status).body(body);
	}
}
