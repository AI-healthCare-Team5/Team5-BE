package com.health.jinryohanjang.domain.intake.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 증상이 시작된 시점을 나타낸다. label은 화면에 보일 이름이자 onsetDetail의 기본값이다.
 */
@Getter
@RequiredArgsConstructor
public enum Onset {
	TODAY("오늘"),
	DAYS_2_3("2~3일 전부터"),
	OVER_WEEK("일주일 이상");

	private final String label;
}
