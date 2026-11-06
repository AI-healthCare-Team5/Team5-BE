package com.health.jinryohanjang.domain.member.dto;

import java.time.OffsetDateTime;

import com.health.jinryohanjang.domain.guardian.enums.Relation;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberInfoResponse(
	@Schema(description = "회원 ID", example = "1")
	Long memberId,

	@Schema(description = "닉네임", example = "봄날", nullable = true)
	String nickname,

	@Schema(description = "로그인 방식", example = "KAKAO")
	String provider,

	@Schema(description = "동의 상태")
	Consents consents,

	@Schema(description = "보관한 기록 수", example = "3")
	long recordCount,

	@Schema(description = "보호자. 없으면 null", nullable = true)
	Guardian guardian
) {

	public record Consents(
		@Schema(description = "기록 저장 동의")
		ConsentStatus record,

		@Schema(description = "보호자 연락처 저장 동의")
		ConsentStatus guardian
	) {
	}

	public record ConsentStatus(
		@Schema(description = "현재 동의했는지", example = "true")
		boolean agreed,

		@Schema(description = "동의한 문구 버전. 동의하지 않았으면 null", example = "v1", nullable = true)
		String policyVersion,

		@Schema(description = "동의한 시각. 동의하지 않았으면 null", example = "2026-10-30T14:00:00+09:00", nullable = true)
		OffsetDateTime agreedAt
	) {
	}

	public record Guardian(
		@Schema(description = "보호자 관계", example = "DAUGHTER")
		Relation relation,

		@Schema(description = "가운데를 가린 전화번호", example = "010-****-5678")
		String phoneNumber
	) {
	}
}
