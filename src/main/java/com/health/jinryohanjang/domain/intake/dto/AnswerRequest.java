package com.health.jinryohanjang.domain.intake.dto;

import com.health.jinryohanjang.domain.intake.enums.InputMode;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnswerRequest(
	@Schema(description = "답하는 질문 ID. 현재 질문과 다르면 INTAKE_409 (버튼 두 번 누름 방지)", example = "q_swelling")
	@NotBlank(message = "질문 ID가 없어요")
	String questionId,

	@Schema(description = "입력 방식. 답변마다 달라도 된다.", example = "BUTTON")
	@NotNull(message = "입력 방식이 없어요")
	InputMode inputMode,

	@Schema(description = "답변 (최대 500자). 말·글은 그대로, 버튼은 YES / NO / UNKNOWN 또는 선택지 code", example = "YES",
		maxLength = IntakeStartRequest.MAX_TEXT_LENGTH)
	@NotBlank(message = "답변을 입력해 주세요")
	String answer
) {
}
