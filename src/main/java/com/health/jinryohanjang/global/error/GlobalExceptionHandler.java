package com.health.jinryohanjang.global.error;

import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

import com.health.jinryohanjang.global.common.response.ApiDomain;
import com.health.jinryohanjang.global.common.response.ApiResponse;
import com.health.jinryohanjang.global.common.response.Domain;
import com.health.jinryohanjang.global.error.InvalidInputResult.FieldErrorDetail;
import com.health.jinryohanjang.global.ratelimit.RateLimitException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_INPUT_MESSAGE = "입력값을 확인해 주세요.";
	private static final String INTERNAL_ERROR_MESSAGE = "일시적인 오류가 생겼어요. 잠시 후 다시 시도해 주세요.";

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
		return ApiResponse.of(e.getDomain(), e.getStatus(), e.getMessage(), e.getResult());
	}

	@ExceptionHandler(RateLimitException.class)
	public ResponseEntity<ApiResponse<Object>> handleRateLimitException(RateLimitException e) {
		ResponseEntity<ApiResponse<Object>> response = handleBusinessException(e);
		return ResponseEntity.status(response.getStatusCode())
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
			.body(response.getBody());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<InvalidInputResult>> handleValidationException(
		MethodArgumentNotValidException e, HandlerMethod handlerMethod) {
		List<FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
			.map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
			.toList();
		return invalidInput(handlerMethod, errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<InvalidInputResult>> handleNotReadableException(
		HttpMessageNotReadableException e, HandlerMethod handlerMethod) {
		return invalidInput(handlerMethod, List.of());
	}

	/**
	 * 처리하지 못한 예외는 {도메인}_500으로 응답한다.
	 * 스프링 표준 예외(없는 경로, 지원하지 않는 메서드 등)와 도메인을 알 수 없는 요청은 스프링 기본 처리에 맡긴다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HandlerMethod handlerMethod)
		throws Exception {
		Domain domain = findDomain(handlerMethod);
		if (e instanceof ErrorResponse || domain == null) {
			throw e;
		}
		log.error("처리하지 못한 예외", e);
		return ApiResponse.of(domain, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, null);
	}

	private ResponseEntity<ApiResponse<InvalidInputResult>> invalidInput(
		HandlerMethod handlerMethod, List<FieldErrorDetail> errors) {
		Domain domain = findDomain(handlerMethod);
		if (domain == null) {
			throw new IllegalStateException("컨트롤러에 @ApiDomain이 없습니다: " + handlerMethod);
		}
		return ApiResponse.of(domain, HttpStatus.BAD_REQUEST, INVALID_INPUT_MESSAGE, new InvalidInputResult(errors));
	}

	private Domain findDomain(HandlerMethod handlerMethod) {
		if (handlerMethod == null) {
			return null;
		}
		ApiDomain apiDomain = handlerMethod.getMethodAnnotation(ApiDomain.class);
		if (apiDomain == null) {
			apiDomain = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiDomain.class);
		}
		return apiDomain == null ? null : apiDomain.value();
	}
}
