package com.health.jinryohanjang.domain.stt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SttResponse(
	@Schema(description = "인식한 글자. 문진 시작이나 답변에 inputMode = VOICE로 보낸다.", example = "무릎이 사흘째 아파요")
	String text,

	@Schema(description = "녹음 길이(초)", example = "3.2")
	double durationSec
) {
}
