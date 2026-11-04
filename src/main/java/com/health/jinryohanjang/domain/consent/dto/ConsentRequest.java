package com.health.jinryohanjang.domain.consent.dto;

import com.health.jinryohanjang.domain.consent.enums.ConsentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentRequest(
	@Schema(description = "동의 종류. RECORD만 가능하다. GUARDIAN은 보호자 등록 API에서 함께 저장한다.", example = "RECORD")
	@NotNull(message = "동의 종류가 없어요")
	ConsentType consentType,

	@Schema(description = "동의한 문구 버전. 서버 설정의 현재 버전과 같아야 한다.", example = "v1")
	@NotBlank(message = "문구 버전이 없어요")
	String policyVersion
) {
}
