package com.health.jinryohanjang.common.status;

import org.springframework.http.HttpStatus;

import com.health.jinryohanjang.common.base.BaseStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성공 응답 목록. API를 만들 때 도메인별로 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum SuccessStatus implements BaseStatus {

	// auth
	AUTHORIZE_URL_SUCCESS("AUTH_200", HttpStatus.OK, "카카오 로그인 URL 조회 성공"),
	LOGIN_SUCCESS("AUTH_200", HttpStatus.OK, "로그인 성공"),
	REFRESH_SUCCESS("AUTH_200", HttpStatus.OK, "토큰 재발급 성공"),
	LOGOUT_SUCCESS("AUTH_200", HttpStatus.OK, "로그아웃 성공"),

	// member
	MEMBER_GET_SUCCESS("MEMBER_200", HttpStatus.OK, "내 정보 조회 성공"),
	MEMBER_WITHDRAW_SUCCESS("MEMBER_200", HttpStatus.OK, "회원 탈퇴 성공"),

	// hospital
	HOSPITAL_SEARCH_SUCCESS("HOSPITAL_200", HttpStatus.OK, "병원 검색 성공"),
	EMERGENCY_ROOM_SUCCESS("HOSPITAL_200", HttpStatus.OK, "응급실 조회 성공"),
	HEALTH_CENTER_SUCCESS("HOSPITAL_200", HttpStatus.OK, "보건소 조회 성공"),
	REGION_LIST_SUCCESS("HOSPITAL_200", HttpStatus.OK, "지역 목록 조회 성공"),

	// intake
	INTAKE_START_SUCCESS("INTAKE_201", HttpStatus.CREATED, "문진 시작 성공"),
	INTAKE_GET_SUCCESS("INTAKE_200", HttpStatus.OK, "문진 조회 성공"),
	INTAKE_ANSWER_SUCCESS("INTAKE_200", HttpStatus.OK, "답변 처리 성공"),
	INTAKE_BACK_SUCCESS("INTAKE_200", HttpStatus.OK, "이전 질문으로 이동"),
	INTAKE_CANCEL_SUCCESS("INTAKE_200", HttpStatus.OK, "문진 취소 성공"),

	// department
	DEPARTMENT_GET_SUCCESS("DEPARTMENT_200", HttpStatus.OK, "추천 진료과 조회 성공"),

	// summary
	SUMMARY_GET_SUCCESS("SUMMARY_200", HttpStatus.OK, "요약지 조회 성공"),
	SUMMARY_UPDATE_SUCCESS("SUMMARY_200", HttpStatus.OK, "요약지 수정 성공"),
	SUMMARY_CONFIRM_SUCCESS("SUMMARY_200", HttpStatus.OK, "요약지 확인 성공"),
	SUMMARY_RETRY_SUCCESS("SUMMARY_202", HttpStatus.ACCEPTED, "요약지 다시 만들기 시작"),
	EMERGENCY_CARD_SUCCESS("SUMMARY_200", HttpStatus.OK, "응급 카드 조회 성공"),
	SUMMARY_SAVE_SUCCESS("SUMMARY_201", HttpStatus.CREATED, "내 기록 보관 성공"),
	RECORD_LIST_SUCCESS("SUMMARY_200", HttpStatus.OK, "내 기록 목록 조회 성공"),
	RECORD_GET_SUCCESS("SUMMARY_200", HttpStatus.OK, "기록 상세 조회 성공"),
	RECORD_DELETE_SUCCESS("SUMMARY_200", HttpStatus.OK, "기록 삭제 성공"),

	// consent
	CONSENT_ALREADY_AGREED("CONSENT_200", HttpStatus.OK, "이미 동의했어요."),
	CONSENT_AGREED("CONSENT_201", HttpStatus.CREATED, "동의 성공"),

	// guardian
	GUARDIAN_SAVE_SUCCESS("GUARDIAN_200", HttpStatus.OK, "보호자 등록 성공"),
	GUARDIAN_DELETE_SUCCESS("GUARDIAN_200", HttpStatus.OK, "보호자 삭제 성공");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;
}
