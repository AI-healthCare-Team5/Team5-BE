package com.health.jinryohanjang.domain.summary.enums;

import java.util.List;

/**
 * 보관한 기록의 복용약·앓는 병·알레르기 확인 상태를 나타낸다.
 * 보관 뒤에도 null("확인 못 했어요")과 [] ("없음")을 구분하려고 둔다.
 */
public enum HealthInfoStatus {
	NOT_ASKED,
	NONE,
	HAS;

	public static HealthInfoStatus of(List<?> values) {
		if (values == null) {
			return NOT_ASKED;
		}
		return values.isEmpty() ? NONE : HAS;
	}
}
