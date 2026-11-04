package com.health.jinryohanjang.domain.intake.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 불편한 부위를 나타낸다. label은 화면에 보일 이름이다.
 */
@Getter
@RequiredArgsConstructor
public enum BodyPart {
	HEAD("머리"),
	CHEST("가슴"),
	ABDOMEN("배"),
	BACK("허리 · 등"),
	ARM_SHOULDER("팔 · 어깨"),
	LEG_KNEE("다리 · 무릎"),
	SKIN("피부"),
	ETC("기타");

	private final String label;
}
