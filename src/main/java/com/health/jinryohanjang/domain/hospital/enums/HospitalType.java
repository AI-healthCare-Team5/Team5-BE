package com.health.jinryohanjang.domain.hospital.enums;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 심평원 종별코드와 이름이다.
 */
@Getter
@RequiredArgsConstructor
public enum HospitalType {
	TERTIARY("01", "상급종합"),
	GENERAL("11", "종합병원"),
	HOSPITAL("21", "병원"),
	NURSING("28", "요양병원"),
	MENTAL("29", "정신병원"),
	CLINIC("31", "의원"),
	DENTAL_HOSPITAL("41", "치과병원"),
	DENTAL_CLINIC("51", "치과의원"),
	MIDWIFERY("61", "조산원"),
	HEALTH_CENTER("71", "보건소"),
	HEALTH_SUBCENTER("72", "보건지소"),
	HEALTH_POST("73", "보건진료소"),
	HEALTH_MEDICAL_CENTER("75", "보건의료원"),
	KOREAN_MEDICINE_HOSPITAL("92", "한방병원"),
	KOREAN_MEDICINE_CLINIC("93", "한의원");

	/**
	 * 가까운 보건소 API가 찾는 보건소 계열 종별코드다.
	 */
	public static final List<String> HEALTH_CENTER_CODES = List.of("71", "72", "73", "75");

	private final String code;
	private final String label;

	public static String labelOf(String code) {
		return Arrays.stream(values())
			.filter(type -> type.code.equals(code))
			.map(HospitalType::getLabel)
			.findFirst()
			.orElse(null);
	}
}
