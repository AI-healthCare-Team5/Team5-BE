package com.health.jinryohanjang.domain.intake.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 위험 신호 규칙(data.sql)이 테스트 문장 목록(red-flag-cases.csv)대로 판정하는지 확인한다.
 * 규칙을 고칠 때는 이 목록에 응급이어야 하는 말과 아니어야 하는 말을 함께 추가한다. 근거는 docs/red-flag/README.md에 있다.
 */
@SpringBootTest
class RedFlagDetectorCasesTest {

	@Autowired
	private RedFlagDetector redFlagDetector;

	@ParameterizedTest(name = "{0} → {1} ({2})")
	@CsvFileSource(resources = "/red-flag-cases.csv", numLinesToSkip = 1, encoding = "UTF-8")
	void detect(String input, String expected, String reason) {
		String actual = redFlagDetector.detect(input).map(Enum::name).orElse("NONE");
		assertThat(actual).as("%s (%s)", input, reason).isEqualTo(expected);
	}
}
