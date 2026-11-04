package com.health.jinryohanjang.domain.guardian.dto;

import com.health.jinryohanjang.domain.guardian.enums.Relation;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GuardianRequest(
	@Schema(description = "보호자 관계", example = "DAUGHTER")
	@NotNull(message = "관계를 선택해 주세요")
	Relation relation,

	@Schema(description = "숫자만 10~11자리. 서버에서 암호화해 저장한다.", example = "01012345678")
	@NotBlank(message = "전화번호를 입력해 주세요")
	@Pattern(regexp = "^\\d{10,11}$", message = "숫자 10~11자리여야 해요")
	String phoneNumber,

	@Schema(description = "보호자 연락처 저장 동의(C01). true여야 저장한다.", example = "true")
	@NotNull(message = "동의에 체크해 주세요")
	@AssertTrue(message = "동의에 체크해 주세요")
	Boolean consentAgreed,

	@Schema(description = "동의한 문구 버전. 서버 설정의 현재 버전과 같아야 한다.", example = "v1")
	@NotBlank(message = "문구 버전이 없어요")
	String policyVersion
) {
}
