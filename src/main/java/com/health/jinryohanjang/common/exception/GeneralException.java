package com.health.jinryohanjang.common.exception;

import com.health.jinryohanjang.common.base.BaseStatus;

import lombok.Getter;

/**
 * 서비스에서 실패 응답을 내려야 할 때 던지는 예외
 * 실패 result는 기본 null이고, 같은 code 안에서 이유를 구분해야 할 때만 result를 담는다.
 */
@Getter
public class GeneralException extends RuntimeException {

	private final BaseStatus errorStatus;
	private final Object result;
	private final Long retryAfterSeconds;

	public GeneralException(BaseStatus errorStatus) {
		this(errorStatus, null);
	}

	public GeneralException(BaseStatus errorStatus, Object result) {
		this(errorStatus, result, null);
	}

	private GeneralException(BaseStatus errorStatus, Object result, Long retryAfterSeconds) {
		super(errorStatus.getMessage());
		this.errorStatus = errorStatus;
		this.result = result;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public static GeneralException tooManyRequests(BaseStatus errorStatus, long retryAfterSeconds) {
		return new GeneralException(errorStatus, null, retryAfterSeconds);
	}
}
