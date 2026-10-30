package com.health.jinryohanjang.common.status;

import org.springframework.http.HttpStatus;

import com.health.jinryohanjang.common.base.BaseStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 실패 응답 목록 (노션 "에러 code 목록" 기준)
 * 요청 값 오류({도메인}_400)와 서버 오류({도메인}_500)는 GeneralExceptionAdvice가 만든다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorStatus implements BaseStatus {

	// auth
	LOGIN_REQUIRED("AUTH_401", HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
	TOKEN_EXPIRED("AUTH_401", HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요."),
	TOKEN_INVALID("AUTH_401", HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않아요."),
	REFRESH_TOKEN_INVALID("AUTH_401", HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
	KAKAO_CODE_INVALID("AUTH_400", HttpStatus.BAD_REQUEST, "카카오 로그인에 실패했어요. 다시 시도해 주세요."),
	OAUTH_STATE_MISMATCH("AUTH_400", HttpStatus.BAD_REQUEST, "로그인 요청이 올바르지 않아요. 다시 시도해 주세요."),
	REDIRECT_URI_NOT_ALLOWED("AUTH_400", HttpStatus.BAD_REQUEST, "허용되지 않은 redirectUri예요."),
	KAKAO_UNAVAILABLE("AUTH_502", HttpStatus.BAD_GATEWAY, "카카오 서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요."),

	// intake
	INTAKE_INVALID_ANSWER("INTAKE_400", HttpStatus.BAD_REQUEST, "답변 형식이 올바르지 않아요. 다시 입력해 주세요."),
	INTAKE_NO_PREVIOUS("INTAKE_400", HttpStatus.BAD_REQUEST, "이전 질문이 없어요."),
	INTAKE_FORBIDDEN("INTAKE_403", HttpStatus.FORBIDDEN, "접근할 수 없는 문진이에요."),
	INTAKE_NOT_FOUND("INTAKE_404", HttpStatus.NOT_FOUND, "문진을 찾을 수 없어요. 처음부터 다시 시작해 주세요."),
	INTAKE_ALREADY_FINISHED("INTAKE_409", HttpStatus.CONFLICT, "이미 끝난 문진이에요."),
	INTAKE_DUPLICATE_ANSWER("INTAKE_409", HttpStatus.CONFLICT, "이미 처리된 답변이에요. 화면을 새로 불러올게요."),
	INTAKE_NOT_UNDERSTOOD("INTAKE_422", HttpStatus.UNPROCESSABLE_CONTENT, "다시 말씀해 주세요."),
	INTAKE_TOO_MANY_REQUESTS("INTAKE_429", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
	INTAKE_AI_UNAVAILABLE("INTAKE_503", HttpStatus.SERVICE_UNAVAILABLE, "지금은 답변을 만들 수 없어요. 잠시 후 다시 시도해 주세요."),

	// department
	DEPARTMENT_FORBIDDEN("DEPARTMENT_403", HttpStatus.FORBIDDEN, "접근할 수 없는 문진이에요."),
	DEPARTMENT_NOT_FOUND("DEPARTMENT_404", HttpStatus.NOT_FOUND, "문진을 찾을 수 없어요. 처음부터 다시 시작해 주세요."),
	DEPARTMENT_NOT_COMPLETED("DEPARTMENT_409", HttpStatus.CONFLICT, "아직 문진이 끝나지 않았어요."),
	DEPARTMENT_AI_UNAVAILABLE("DEPARTMENT_503", HttpStatus.SERVICE_UNAVAILABLE, "지금은 진료과를 추천할 수 없어요. 잠시 후 다시 시도해 주세요."),

	// summary
	SUMMARY_FORBIDDEN("SUMMARY_403", HttpStatus.FORBIDDEN, "접근할 수 없는 요약지예요."),
	SUMMARY_NOT_FOUND("SUMMARY_404", HttpStatus.NOT_FOUND, "요약지를 찾을 수 없어요. 처음부터 다시 시작해 주세요."),
	RECORD_FORBIDDEN("SUMMARY_403", HttpStatus.FORBIDDEN, "접근할 수 없는 기록이에요."),
	RECORD_NOT_FOUND("SUMMARY_404", HttpStatus.NOT_FOUND, "기록을 찾을 수 없어요."),
	SUMMARY_NOT_READY("SUMMARY_409", HttpStatus.CONFLICT, "요약지가 아직 준비되지 않았어요."),
	SUMMARY_ALREADY_SAVED("SUMMARY_409", HttpStatus.CONFLICT, "이미 내 기록에 보관한 요약지예요."),
	SUMMARY_NOT_EMERGENCY("SUMMARY_409", HttpStatus.CONFLICT, "응급 상황으로 판정된 문진이 아니에요."),
	SUMMARY_NOT_FAILED("SUMMARY_409", HttpStatus.CONFLICT, "요약지를 다시 만들 필요가 없어요."),
	SUMMARY_AI_UNAVAILABLE("SUMMARY_503", HttpStatus.SERVICE_UNAVAILABLE, "지금은 요약지를 만들 수 없어요. 잠시 후 다시 시도해 주세요."),

	// consent
	CONSENT_RECORD_REQUIRED("CONSENT_403", HttpStatus.FORBIDDEN, "기록 저장에 동의해야 보관할 수 있어요."),

	// guardian
	GUARDIAN_NOT_FOUND("GUARDIAN_404", HttpStatus.NOT_FOUND, "등록된 보호자가 없어요."),

	// stt
	STT_FILE_REQUIRED("STT_400", HttpStatus.BAD_REQUEST, "녹음 파일이 없어요. 다시 녹음해 주세요."),
	STT_FILE_TOO_LARGE("STT_413", HttpStatus.CONTENT_TOO_LARGE, "녹음이 너무 길어요. 짧게 다시 말씀해 주세요."),
	STT_UNSUPPORTED_FORMAT("STT_415", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 녹음 형식이에요. 글로 입력해 주세요."),
	STT_NOT_RECOGNIZED("STT_422", HttpStatus.UNPROCESSABLE_CONTENT, "다시 말씀해 주세요."),
	STT_TOO_MANY_REQUESTS("STT_429", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 글로 입력해 주세요."),
	STT_UNAVAILABLE("STT_503", HttpStatus.SERVICE_UNAVAILABLE, "음성 인식을 할 수 없어요. 글로 입력해 주세요.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;
}
