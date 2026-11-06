package com.health.jinryohanjang.domain.intake.dto;

import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.InputMode;
import com.health.jinryohanjang.domain.intake.enums.Onset;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

public record IntakeStartRequest(
	@Schema(description = "입력 방식. VOICE(말), TEXT(글), BUTTON(버튼)", example = "TEXT")
	@NotNull(message = "입력 방식이 없어요")
	InputMode inputMode,

	@Schema(description = "말·글로 입력한 증상 (VOICE·TEXT일 때 필수, 최대 500자). BUTTON일 때는 \"더 알려주실 내용\"",
		example = "무릎이 사흘째 아파요", nullable = true, maxLength = MAX_TEXT_LENGTH)
	String text,

	@Schema(description = "불편한 부위 (BUTTON일 때 필수)", example = "LEG_KNEE", nullable = true)
	BodyPart bodyPart,

	@Schema(description = "언제부터 (BUTTON일 때 필수)", example = "DAYS_2_3", nullable = true)
	Onset onset
) {

	/**
	 * 말·글 입력의 최대 길이다. 말로 입력하면 STT 최대 60초가 300~400자라 여유를 둔다. 답변(answer)도 같다.
	 */
	public static final int MAX_TEXT_LENGTH = 500;
}
