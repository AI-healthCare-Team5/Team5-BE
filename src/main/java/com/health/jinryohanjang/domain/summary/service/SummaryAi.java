package com.health.jinryohanjang.domain.summary.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.health.jinryohanjang.common.util.BedrockChatClient;
import com.health.jinryohanjang.domain.summary.model.Summary.HealthInfo;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 요약지에 필요한 LLM 호출(증상 항목 정리, 복용약·앓는 병·알레르기 정리)을 맡는다.
 * 사용자가 한 말을 숫자로 바꾸거나 추측하지 않도록 하고, 형식이 틀린 응답은 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SummaryAi {

	private static final int MAX_TOKENS = 800;
	private static final int LABEL_MAX_LENGTH = 20;

	private static final String COMMON_RULES = """
		너는 어르신이 의사에게 보여 줄 진료 요약지를 정리하는 도우미다.
		- 진단하거나 병명을 추측하지 않는다.
		- 사용자가 한 말에 없는 내용을 더하지 않는다.
		- 날짜·기간·횟수·정도는 사용자가 한 말 그대로 쓰고 숫자로 바꾸지 않는다 (예: "사흘 전부터"는 그대로 "사흘 전부터").
		- 반드시 JSON 객체 하나만 출력한다. 설명이나 코드 블록 표시를 붙이지 않는다.
		""";

	private final BedrockChatClient bedrockChatClient;

	/**
	 * 증상 문답마다 짧은 항목 이름(label)을 붙이고, 자유 답은 짧게 정리한다. 응답에 없는 order는 결과에서 빠진다.
	 */
	public Map<Integer, ItemText> summarizeItems(List<QuestionAnswer> answers) {
		String system = COMMON_RULES + """
			할 일: 증상 문답마다 요약지에 쓸 항목 이름과 답을 정리한다.
			출력: {"items": [{"order": 번호, "label": "항목 이름(명사형, 10자 안, 예: 붓기, 넘어진 적, 통증 정도)", "value": "답을 짧게 정리"}]}
			- 입력의 order마다 하나씩 쓴다.
			- value는 의사가 바로 읽을 수 있게 짧게 쓰되 사용자가 한 말의 뜻을 바꾸지 않는다.
			""";
		List<String> lines = new ArrayList<>();
		for (QuestionAnswer answer : answers) {
			lines.add("%d. 질문: %s / 답: %s".formatted(answer.order(), answer.question(), answer.answer()));
		}
		JsonNode result = bedrockChatClient.askJson(system, String.join("\n", lines), MAX_TOKENS);
		Map<Integer, ItemText> items = new HashMap<>();
		for (JsonNode item : result.path("items")) {
			JsonNode order = item.get("order");
			String label = text(item, "label");
			if (order == null || !order.isInt() || label == null) {
				continue;
			}
			items.put(order.asInt(), new ItemText(cut(label, LABEL_MAX_LENGTH), text(item, "value")));
		}
		return items;
	}

	/**
	 * 기본 정보 질문의 자유 답에서 복용약·앓는 병·알레르기를 [{name, detail}]로 정리한다.
	 * "없어요"는 [], 모른다고 하면 null이다.
	 */
	public HealthInfoResult summarizeHealthInfo(String medications, String conditions, String allergies) {
		String system = COMMON_RULES + """
			할 일: 복용약, 앓는 병, 알레르기 답을 목록으로 정리한다.
			출력: {"medications": 목록, "conditions": 목록, "allergies": 목록}
			목록: [{"name": "이름(짧게)", "detail": "복용 시간·종류 같은 자세한 내용 또는 null"}]
			- 없다고 하면 [] 이다.
			- 모른다고 하거나 답을 알아볼 수 없으면 null이다.
			- 있다고 했지만 확실하지 않다고 하면(예: "당뇨 있다고 들었는데 잘 모르겠어요") 그 항목의 detail에 "확실하지 않음"을 쓴다.
			- 사용자가 말한 이름 그대로 쓴다 (예: "혈압약"을 약 성분 이름으로 바꾸지 않는다).
			""";
		String user = """
			복용약 답: %s
			앓는 병 답: %s
			알레르기 답: %s
			""".formatted(medications, conditions, allergies);
		JsonNode result = bedrockChatClient.askJson(system, user, MAX_TOKENS);
		return new HealthInfoResult(healthInfo(result, "medications"), healthInfo(result, "conditions"),
			healthInfo(result, "allergies"));
	}

	private List<HealthInfo> healthInfo(JsonNode result, String field) {
		JsonNode node = result.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isArray()) {
			throw new BedrockChatClient.AiUnavailableException(field + " 형식이 올바르지 않습니다.");
		}
		List<HealthInfo> values = new ArrayList<>();
		for (JsonNode value : node) {
			String name = text(value, "name");
			if (name != null) {
				values.add(new HealthInfo(name, text(value, "detail")));
			}
		}
		return values;
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isString()) {
			return null;
		}
		String text = value.asString().trim();
		return text.isEmpty() || "null".equals(text) ? null : text;
	}

	private String cut(String value, int maxLength) {
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	public record QuestionAnswer(int order, String question, String answer) {
	}

	public record ItemText(String label, String value) {
	}

	public record HealthInfoResult(List<HealthInfo> medications, List<HealthInfo> conditions,
		List<HealthInfo> allergies) {
	}
}
