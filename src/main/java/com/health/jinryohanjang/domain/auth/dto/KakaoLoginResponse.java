package com.health.jinryohanjang.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginResponse(
	@Schema(description = "회원 ID", example = "1")
	Long memberId,

	@Schema(description = "카카오 닉네임. 제공에 동의하지 않았으면 null", example = "봄날", nullable = true)
	String nickname,

	@Schema(description = "처음 가입한 회원인지", example = "true")
	boolean isNewMember,

	@Schema(description = "기록 저장 동의(C02)가 필요한지", example = "true")
	boolean needsRecordConsent
) {
}
