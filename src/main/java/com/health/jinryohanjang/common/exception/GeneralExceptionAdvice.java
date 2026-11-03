package com.health.jinryohanjang.common.exception;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.health.jinryohanjang.common.response.ApiResponse;
import com.health.jinryohanjang.common.status.ErrorStatus;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

/**
 * 전역 예외 처리
 * 정해 둔 실패는 ErrorStatus로, 요청 값 오류와 서버 오류는 {도메인}_{HTTP 상태}로 응답한다.
 * 도메인은 요청을 처리한 컨트롤러의 패키지(domain.{도메인}.controller)에서 읽는다.
 */
@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice extends ResponseEntityExceptionHandler {

	private static final Pattern DOMAIN_PACKAGE = Pattern.compile("\\.domain\\.([a-z]+)\\.");
	private static final String REQUIRED = "필수 값이에요";
	private static final String INVALID_FORMAT = "형식이 올바르지 않아요";

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

	/**
	 * multipart 형식 오류는 클라이언트 잘못이므로 {도메인}_400으로 응답한다.
	 * 크기 초과는 handleMaxUploadSizeExceededException이 처리한다.
	 */
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<Object> handleMultipartException(MultipartException e, WebRequest request) {
		return handleExceptionInternal(e, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
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

	/**
	 * 크기 초과는 컨트롤러를 찾기 전에 발생해 도메인을 읽을 수 없다. 업로드 API는 STT뿐이므로 STT_413으로 응답한다.
	 */
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
		MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErrorStatus error = ErrorStatus.STT_FILE_TOO_LARGE;
		ApiResponse<Object> response = new ApiResponse<>(false, error.getCode(), error.getMessage(), null);
		return ResponseEntity.status(error.getHttpStatus()).headers(headers).body(response);
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
			return e.getBindingResult().getAllErrors().stream()
				.map(error -> toDetail(error, ""))
				.toList();
		}
		if (ex instanceof HandlerMethodValidationException e) {
			return e.getParameterValidationResults().stream()
				.flatMap(result -> detailsOf(result).stream())
				.toList();
		}
		if (ex instanceof MissingServletRequestParameterException e) {
			return List.of(new FieldErrorDetail(e.getParameterName(), REQUIRED));
		}
		if (ex instanceof MissingServletRequestPartException e) {
			return List.of(new FieldErrorDetail(e.getRequestPartName(), REQUIRED));
		}
		if (ex instanceof MissingRequestCookieException e) {
			return List.of(new FieldErrorDetail(e.getCookieName(), REQUIRED));
		}
		if (ex instanceof MissingRequestHeaderException e) {
			return List.of(new FieldErrorDetail(e.getHeaderName(), REQUIRED));
		}
		if (ex instanceof TypeMismatchException e) {
			return List.of(new FieldErrorDetail(e.getPropertyName(), INVALID_FORMAT));
		}
		if (ex instanceof HttpMessageNotReadableException e) {
			return List.of(new FieldErrorDetail(bodyFieldOf(e), INVALID_FORMAT));
		}
		if (ex instanceof MultipartException) {
			return List.of(new FieldErrorDetail("file", INVALID_FORMAT));
		}
		return List.of();
	}

	private List<FieldErrorDetail> detailsOf(ParameterValidationResult result) {
		if (result instanceof ParameterErrors errors) {
			String prefix = errors.getContainerIndex() == null ? "" : "[" + errors.getContainerIndex() + "].";
			return errors.getAllErrors().stream()
				.map(error -> toDetail(error, prefix))
				.toList();
		}
		String field = parameterNameOf(result.getMethodParameter());
		return result.getResolvableErrors().stream()
			.map(error -> new FieldErrorDetail(field, error.getDefaultMessage()))
			.toList();
	}

	private FieldErrorDetail toDetail(ObjectError error, String prefix) {
		if (error instanceof FieldError fieldError) {
			return new FieldErrorDetail(prefix + fieldError.getField(),
				fieldError.isBindingFailure() ? INVALID_FORMAT : fieldError.getDefaultMessage());
		}
		return new FieldErrorDetail(prefix + error.getObjectName(), error.getDefaultMessage());
	}

	/**
	 * 클라이언트가 보낸 이름으로 응답하도록 어노테이션에 지정한 이름을 먼저 쓴다.
	 */
	private String parameterNameOf(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && StringUtils.hasText(requestParam.name())) {
			return requestParam.name();
		}
		CookieValue cookieValue = parameter.getParameterAnnotation(CookieValue.class);
		if (cookieValue != null && StringUtils.hasText(cookieValue.name())) {
			return cookieValue.name();
		}
		RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
		if (requestHeader != null && StringUtils.hasText(requestHeader.name())) {
			return requestHeader.name();
		}
		return parameter.getParameterName();
	}

	/**
	 * 값의 형식이 틀리면 해당 필드를, 본문 자체가 없거나 깨졌으면 body를 돌려준다.
	 */
	private String bodyFieldOf(HttpMessageNotReadableException ex) {
		if (ex.getCause() instanceof JacksonException cause) {
			StringBuilder field = new StringBuilder();
			for (JacksonException.Reference reference : cause.getPath()) {
				if (reference.getPropertyName() != null) {
					field.append(field.isEmpty() ? "" : ".").append(reference.getPropertyName());
				} else {
					field.append("[").append(reference.getIndex()).append("]");
				}
			}
			if (!field.isEmpty()) {
				return field.toString();
			}
		}
		return "body";
	}
}
