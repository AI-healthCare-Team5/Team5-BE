package com.health.jinryohanjang.common.exception;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.health.jinryohanjang.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리
 * 정해 둔 실패는 ErrorStatus로, 요청 값 오류와 서버 오류는 {도메인}_{HTTP 상태}로 응답한다.
 * 도메인은 요청을 처리한 컨트롤러의 패키지(domain.{도메인}.controller)에서 읽는다.
 */
@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice extends ResponseEntityExceptionHandler {

	private static final Pattern DOMAIN_PACKAGE = Pattern.compile("\\.domain\\.([a-z]+)\\.");

	public record FieldErrorDetail(String field, String reason) {
	}

	@ExceptionHandler(GeneralException.class)
	public ResponseEntity<ApiResponse<Object>> handleGeneralException(GeneralException e) {
		ResponseEntity<ApiResponse<Object>> response = ApiResponse.error(e.getErrorStatus(), e.getResult());
		if (e.getRetryAfterSeconds() == null) {
			return response;
		}
		return ResponseEntity.status(response.getStatusCode())
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
			.body(response.getBody());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpectedException(Exception e, WebRequest request) {
		log.error("처리하지 못한 예외", e);
		return handleExceptionInternal(e, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	/**
	 * 스프링 표준 예외도 모두 이 메서드를 지나므로 여기서 공통 응답 형식으로 바꾼다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
		Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		boolean badRequest = status.value() == HttpStatus.BAD_REQUEST.value();
		ApiResponse<Object> response = new ApiResponse<>(
			false,
			domainOf(request) + "_" + status.value(),
			messageOf(status),
			badRequest ? Map.of("errors", fieldErrorsOf(ex)) : null);
		return super.handleExceptionInternal(ex, response, headers, status, request);
	}

	private String domainOf(WebRequest request) {
		Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
			RequestAttributes.SCOPE_REQUEST);
		if (handler instanceof HandlerMethod handlerMethod) {
			Matcher matcher = DOMAIN_PACKAGE.matcher(handlerMethod.getBeanType().getName());
			if (matcher.find()) {
				return matcher.group(1).toUpperCase(Locale.ROOT);
			}
		}
		return "COMMON";
	}

	private String messageOf(HttpStatusCode status) {
		if (status.value() == HttpStatus.BAD_REQUEST.value()) {
			return "입력값을 확인해 주세요.";
		}
		return status.is5xxServerError() ? "일시적인 오류가 생겼어요. 잠시 후 다시 시도해 주세요." : "요청을 처리할 수 없어요.";
	}

	private List<FieldErrorDetail> fieldErrorsOf(Exception ex) {
		if (ex instanceof MethodArgumentNotValidException e) {
			return e.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorDetail(error.getField(),
					error.isBindingFailure() ? "형식이 올바르지 않아요" : error.getDefaultMessage()))
				.toList();
		}
		if (ex instanceof HandlerMethodValidationException e) {
			return e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
					.map(error -> new FieldErrorDetail(result.getMethodParameter().getParameterName(),
						error.getDefaultMessage())))
				.toList();
		}
		if (ex instanceof MissingServletRequestParameterException e) {
			return List.of(new FieldErrorDetail(e.getParameterName(), "필수 값이에요"));
		}
		if (ex instanceof MissingServletRequestPartException e) {
			return List.of(new FieldErrorDetail(e.getRequestPartName(), "필수 값이에요"));
		}
		if (ex instanceof TypeMismatchException e) {
			return List.of(new FieldErrorDetail(e.getPropertyName(), "형식이 올바르지 않아요"));
		}
		return List.of();
	}
}
