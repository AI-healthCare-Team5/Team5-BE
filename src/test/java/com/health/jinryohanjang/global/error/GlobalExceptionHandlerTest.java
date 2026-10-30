package com.health.jinryohanjang.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.health.jinryohanjang.global.common.response.ApiDomain;
import com.health.jinryohanjang.global.common.response.ApiResponse;
import com.health.jinryohanjang.global.common.response.Domain;
import com.health.jinryohanjang.global.ratelimit.RateLimitException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void 성공_code는_도메인과_HTTP_상태로_만든다() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("INTAKE_201"))
			.andExpect(jsonPath("$.result.name").value("a"));
	}

	@Test
	void 검증_실패는_도메인_400과_errors를_반환한다() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("INTAKE_400"))
			.andExpect(jsonPath("$.result.errors[0].field").value("name"))
			.andExpect(jsonPath("$.result.errors[0].reason").exists());
	}

	@Test
	void 비즈니스_예외는_지정한_code와_result를_반환한다() throws Exception {
		mockMvc.perform(post("/test/finished"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INTAKE_409"))
			.andExpect(jsonPath("$.message").value("이미 끝난 문진이에요."))
			.andExpect(jsonPath("$.result.status").value("COMPLETED"));
	}

	@Test
	void 다른_도메인_code로_실패할_수_있다() throws Exception {
		mockMvc.perform(post("/test/unauthorized"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_401"))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@Test
	void 호출_제한은_도메인_429와_Retry_After를_반환한다() throws Exception {
		mockMvc.perform(post("/test/rate-limited"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "13"))
			.andExpect(jsonPath("$.code").value("INTAKE_429"))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@Test
	void 처리하지_못한_예외는_도메인_500을_반환한다() throws Exception {
		mockMvc.perform(post("/test/error"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTAKE_500"))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	record EchoRequest(@NotBlank String name) {
	}

	@RestController
	@ApiDomain(Domain.INTAKE)
	static class TestController {

		@PostMapping("/test/echo")
		ResponseEntity<ApiResponse<EchoRequest>> echo(@Valid @RequestBody EchoRequest request) {
			return ApiResponse.created(Domain.INTAKE, "문진 시작 성공", request);
		}

		@PostMapping("/test/finished")
		ResponseEntity<ApiResponse<Void>> finished() {
			throw new BusinessException(Domain.INTAKE, HttpStatus.CONFLICT, "이미 끝난 문진이에요.",
				Map.of("status", "COMPLETED"));
		}

		@PostMapping("/test/unauthorized")
		ResponseEntity<ApiResponse<Void>> unauthorized() {
			throw new BusinessException(Domain.AUTH, HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
		}

		@PostMapping("/test/rate-limited")
		ResponseEntity<ApiResponse<Void>> rateLimited() {
			throw new RateLimitException(Domain.INTAKE, 13);
		}

		@PostMapping("/test/error")
		ResponseEntity<ApiResponse<Void>> error() {
			throw new IllegalStateException("test");
		}
	}
}
