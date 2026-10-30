package com.health.jinryohanjang.common.response;

import org.springframework.http.ResponseEntity;

import com.health.jinryohanjang.common.base.BaseStatus;

/**
 * 모든 API의 공통 응답 형식
 * 값이 없는 result도 빠지지 않고 null로 내려간다.
 */
public record ApiResponse<T>(
	boolean isSuccess,
	String code,
	String message,
	T result
) {

	public static ResponseEntity<ApiResponse<Void>> success(BaseStatus status) {
		return success(status, null);
	}

	public static <T> ResponseEntity<ApiResponse<T>> success(BaseStatus status, T result) {
		return ResponseEntity.status(status.getHttpStatus())
			.body(new ApiResponse<>(true, status.getCode(), status.getMessage(), result));
	}

	public static ResponseEntity<ApiResponse<Void>> error(BaseStatus status) {
		return error(status, null);
	}

	public static <T> ResponseEntity<ApiResponse<T>> error(BaseStatus status, T result) {
		return ResponseEntity.status(status.getHttpStatus())
			.body(new ApiResponse<>(false, status.getCode(), status.getMessage(), result));
	}
}
