package com.health.jinryohanjang.global.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ApiResponseJsonTest {

	@Autowired
	private ObjectMapper objectMapper;

	record Sample(OffsetDateTime savedAt, String extraNote) {
	}

	@Test
	void 날짜는_오프셋을_붙이고_값이_없는_키도_null로_내린다() {
		OffsetDateTime savedAt = OffsetDateTime.of(2026, 10, 30, 14, 20, 0, 0, ZoneOffset.ofHours(9));
		ApiResponse<Sample> body = ApiResponse.ok(Domain.SUMMARY, "조회 성공", new Sample(savedAt, null)).getBody();

		String json = objectMapper.writeValueAsString(body);

		assertThat(json).contains("\"isSuccess\":true")
			.contains("\"code\":\"SUMMARY_200\"")
			.contains("\"savedAt\":\"2026-10-30T14:20:00+09:00\"")
			.contains("\"extraNote\":null");
	}
}
