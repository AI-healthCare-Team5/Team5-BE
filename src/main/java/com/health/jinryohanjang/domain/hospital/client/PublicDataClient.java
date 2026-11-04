package com.health.jinryohanjang.domain.hospital.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * 공공데이터포털(심평원 병원정보·상세정보, 국립중앙의료원 응급의료기관) API를 부른다.
 * 동기화 작업에서만 쓰고, 사용자 요청 중에는 부르지 않는다.
 */
@Component
public class PublicDataClient {

	private static final String BASE_URL = "https://apis.data.go.kr";
	private static final String HOSPITAL_LIST_PATH = "/B551182/hospInfoServicev2/getHospBasisList";
	private static final String DEPARTMENT_PATH = "/B551182/MadmDtlInfoService2.8/getDgsbjtInfo2.8";
	private static final String DETAIL_PATH = "/B551182/MadmDtlInfoService2.8/getDtlInfo2.8";
	private static final String EMERGENCY_ROOM_PATH = "/B552657/ErmctInfoInqireService/getEgytListInfoInqire";
	private static final String GANGWON_SIDO_CODE = "320000";
	private static final String GANGWON_SIDO_NAME = "강원특별자치도";
	private static final String SUCCESS_CODE = "00";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final RestClient restClient;
	private final String serviceKey;

	public PublicDataClient(@Value("${app.public-data.service-key:}") String serviceKey) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.serviceKey = serviceKey;
	}

	public boolean isConfigured() {
		return StringUtils.hasText(serviceKey);
	}

	public Page getHospitals(int pageNo, int numOfRows) {
		JsonNode body = call(HOSPITAL_LIST_PATH, Map.of(
			"sidoCd", GANGWON_SIDO_CODE, "pageNo", String.valueOf(pageNo), "numOfRows", String.valueOf(numOfRows)));
		return new Page(body.path("totalCount").asInt(), items(body));
	}

	public List<JsonNode> getDepartments(String ykiho) {
		return items(call(DEPARTMENT_PATH, Map.of("ykiho", ykiho, "numOfRows", "100")));
	}

	public JsonNode getDetail(String ykiho) {
		List<JsonNode> items = items(call(DETAIL_PATH, Map.of("ykiho", ykiho)));
		return items.isEmpty() ? null : items.get(0);
	}

	public List<JsonNode> getEmergencyRooms() {
		return items(call(EMERGENCY_ROOM_PATH, Map.of("Q0", GANGWON_SIDO_NAME, "numOfRows", "500")));
	}

	private JsonNode call(String path, Map<String, String> params) {
		Map<String, String> query = new LinkedHashMap<>();
		query.put("serviceKey", serviceKey);
		query.put("_type", "json");
		query.putAll(params);
		String queryString = query.entrySet().stream()
			.map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		JsonNode root = restClient.get()
			.uri(URI.create(BASE_URL + path + "?" + queryString))
			.retrieve()
			.body(JsonNode.class);
		JsonNode response = root == null ? null : root.path("response");
		String resultCode = response == null ? null : response.path("header").path("resultCode").asString();
		if (!SUCCESS_CODE.equals(resultCode)) {
			throw new PublicDataException(path + " 응답 오류: " + resultCode);
		}
		return response.path("body");
	}

	/**
	 * item이 하나면 객체, 여러 개면 배열로 와서 목록으로 맞춘다.
	 */
	private List<JsonNode> items(JsonNode body) {
		JsonNode item = body.path("items").path("item");
		List<JsonNode> items = new ArrayList<>();
		if (item.isArray()) {
			item.forEach(items::add);
		} else if (item.isObject()) {
			items.add(item);
		}
		return items;
	}

	public record Page(int totalCount, List<JsonNode> items) {
	}

	public static class PublicDataException extends RuntimeException {

		public PublicDataException(String message) {
			super(message);
		}
	}
}
