package com.health.jinryohanjang.global.error;

import java.util.List;

/**
 * COMMON_INVALID_INPUT 실패 응답의 result
 */
public record InvalidInputResult(List<FieldErrorDetail> errors) {

	public record FieldErrorDetail(String field, String reason) {
	}
}
