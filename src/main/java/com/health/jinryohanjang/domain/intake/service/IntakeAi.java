package com.health.jinryohanjang.domain.intake.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.util.BedrockChatClient;
import com.health.jinryohanjang.domain.intake.enums.AnswerType;
import com.health.jinryohanjang.domain.intake.enums.BodyPart;
import com.health.jinryohanjang.domain.intake.enums.Onset;
import com.health.jinryohanjang.domain.intake.model.IntakeSession;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Option;
import com.health.jinryohanjang.domain.intake.model.IntakeSession.Question;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 문진에 필요한 LLM 호출(첫 증상 이해, 증상 질문 만들기, 답 해석, 진료과 추천)을 맡는다.
 * LLM 응답의 enum·code는 여기서 검사해서, 틀린 값은 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class IntakeAi {

	private static final int MAX_TOKENS = 600;

	private static final String COMMON_RULES = """
		너는 강원 지역 어르신이 병원에 가기 전에 증상을 정리하도록 돕는 문진 도우미다.
		- 진단하거나 병명을 말하지 않는다.
		- 질문은 한 번에 하나, 존댓말 해요체로 짧게(40자 안) 쓴다.
		- 나이대, 성별, 복용약, 앓는 병, 알레르기는 나중에 따로 물으니 묻지 않는다.
		- 날짜·기간·정도는 사용자가 한 말 그대로 쓰고 숫자로 바꾸거나 추측하지 않는다 (예: "사흘 전부터").
		- 반드시 JSON 객체 하나만 출력한다. 설명이나 코드 블록 표시를 붙이지 않는다.
		""";

	private static final String QUESTION_FORMAT = """
		질문 형식: {"questionId": "q_영문_snake_case", "text": "질문", "answerType": "YES_NO_UNKNOWN|SINGLE_CHOICE|FREE_TEXT", "options": [{"code": "UPPER_SNAKE", "label": "보기"}] 또는 null}
		- 네/아니요로 답할 수 있으면 YES_NO_UNKNOWN을 쓰고 options는 null이다.
		- 보기에서 고르는 질문만 SINGLE_CHOICE이고 options는 2~5개다.
		- 자유롭게 말해야 하는 질문만 FREE_TEXT다.
		""";

	private final BedrockChatClient bedrockChatClient;

	/**
	 * 첫 입력(말·글)에서 증상을 이해하고 첫 증상 질문을 만든다.
	 */
	public StartResult understandSymptom(String text) {
		String system = COMMON_RULES + QUESTION_FORMAT + """
			할 일: 사용자의 첫 말에서 증상을 정리하고 첫 질문을 만든다.
			출력: {"understood": true|false, "mainSymptom": "주된 증상 짧게", "bodyPart": "%s 중 하나 또는 null",
			"onset": "%s 중 하나 또는 null", "onsetDetail": "시작 시점 원문 또는 null", "collected": ["화면에 보일 짧은 요약 1~3개"],
			"firstQuestion": 질문 형식}
			- 증상과 관계없는 말이거나 알아들을 수 없으면 understood는 false이고 나머지는 null이다.
			""".formatted(enumNames(BodyPart.values()), enumNames(Onset.values()));
		JsonNode result = bedrockChatClient.askJson(system, "사용자의 첫 말: " + text, MAX_TOKENS);
		if (!result.path("understood").asBoolean(false)) {
			return StartResult.notUnderstood();
		}
		Question question = question(result.path("firstQuestion"));
		String mainSymptom = text(result, "mainSymptom");
		if (question == null || mainSymptom == null) {
			throw new BedrockChatClient.AiUnavailableException("첫 질문을 만들지 못했습니다.");
		}
		return new StartResult(true, mainSymptom, enumOf(BodyPart.class, text(result, "bodyPart")),
			enumOf(Onset.class, text(result, "onset")), text(result, "onsetDetail"), strings(result.path("collected")),
			question);
	}

	/**
	 * 증상 질문의 답을 해석하고 다음 질문을 만든다. 충분하거나 최대 개수에 닿으면 done이다.
	 */
	public SymptomStep nextSymptomStep(IntakeSession session, String answer, String buttonAnswer) {
		Question current = session.getCurrent();
		boolean lastQuestion = session.answeredCount(session.getStage()) + 1 >= IntakeSession.SYMPTOM_MAX_QUESTIONS;
		String system = COMMON_RULES + QUESTION_FORMAT + """
			할 일: 지금 질문에 대한 사용자의 답을 해석하고, 필요하면 다음 증상 질문을 만든다.
			출력: {"understood": true|false, "normalizedAnswer": "해석한 답", "collected": ["지금까지 모은 정보 짧게 1~6개"],
			"done": true|false, "nextQuestion": 질문 형식 또는 null}
			- YES_NO_UNKNOWN 질문이면 normalizedAnswer는 YES, NO, UNKNOWN 중 하나다.
			- SINGLE_CHOICE 질문이면 normalizedAnswer는 options의 code 중 하나다.
			- FREE_TEXT 질문이면 normalizedAnswer는 답을 짧게 정리한 문장이다.
			- 답을 해석할 수 없으면 understood는 false다.
			- 진료과를 고르고 의사에게 전할 만큼 정보가 모였으면 done은 true이고 nextQuestion은 null이다.
			""" + (lastQuestion ? "- 이번이 마지막 증상 질문이라 done은 반드시 true다.\n" : "");
		String user = """
			첫 말: %s
			주된 증상: %s
			지금까지의 문답:
			%s
			지금 질문: %s (answerType %s%s)
			사용자의 답: %s
			""".formatted(session.getInitialText(), session.getMainSymptom(), history(session), current.text(),
			current.answerType(), options(current), buttonAnswer != null ? buttonAnswer : answer);
		JsonNode result = bedrockChatClient.askJson(system, user, MAX_TOKENS);
		if (!result.path("understood").asBoolean(false)) {
			return SymptomStep.notUnderstood();
		}
		String normalized = buttonAnswer != null ? buttonAnswer : normalize(current, text(result, "normalizedAnswer"));
		if (normalized == null) {
			return SymptomStep.notUnderstood();
		}
		boolean done = lastQuestion || result.path("done").asBoolean(false);
		Question next = done ? null : question(result.path("nextQuestion"));
		return new SymptomStep(true, normalized, strings(result.path("collected")), done || next == null, next);
	}

	/**
	 * 기본 정보 선택지 질문에 말·글로 답했을 때 보기 code로 맞춘다. 맞출 수 없으면 null이다.
	 */
	public String matchOption(Question question, String answer) {
		String system = COMMON_RULES + """
			할 일: 사용자의 답이 보기 중 무엇인지 고른다.
			출력: {"code": "보기 code 또는 null"}
			""";
		String user = "질문: %s%s\n사용자의 답: %s".formatted(question.text(), options(question), answer);
		return normalize(question, text(bedrockChatClient.askJson(system, user, 100), "code"));
	}

	/**
	 * 문진 내용으로 진료과 하나를 고른다. 후보(department 테이블) 밖의 코드는 쓰지 않는다.
	 */
	public DepartmentChoice recommendDepartment(IntakeSession session, Map<String, String> candidates) {
		String list = candidates.entrySet().stream()
			.map(entry -> entry.getKey() + " " + entry.getValue())
			.collect(Collectors.joining(", "));
		String system = COMMON_RULES + """
			할 일: 문진 내용을 보고 먼저 가 볼 진료과 하나를 고른다.
			후보 진료과(코드 이름): %s
			출력: {"departmentCode": "후보 코드 중 하나", "recommendReason": "고른 이유를 해요체 한 문장(80자 안)으로"}
			- 이유에는 병명을 쓰지 않고 증상만 쓴다.
			""".formatted(list);
		String user = """
			주된 증상: %s
			문답:
			%s
			""".formatted(session.getMainSymptom(), history(session));
		for (int attempt = 0; attempt < 2; attempt++) {
			JsonNode result = bedrockChatClient.askJson(system, user, 300);
			String code = text(result, "departmentCode");
			String reason = text(result, "recommendReason");
			if (code != null && candidates.containsKey(code) && reason != null) {
				return new DepartmentChoice(code, reason);
			}
		}
		throw new BedrockChatClient.AiUnavailableException("후보에 없는 진료과를 골랐습니다.");
	}

	private String history(IntakeSession session) {
		List<String> lines = new ArrayList<>();
		for (IntakeSession.Answered answer : session.getAnswered()) {
			lines.add("- " + answer.question().text() + " → " + readable(answer.question(), answer.normalizedAnswer()));
		}
		return lines.isEmpty() ? "(없음)" : String.join("\n", lines);
	}

	/**
	 * AI가 답을 헷갈리지 않도록 YES·NO 같은 code를 사람이 읽는 말로 바꾼다.
	 */
	private String readable(Question question, String answer) {
		if (question.answerType() == AnswerType.YES_NO_UNKNOWN) {
			return switch (answer) {
				case "YES" -> "네";
				case "NO" -> "아니요";
				default -> "잘 모르겠어요";
			};
		}
		if (question.answerType() == AnswerType.SINGLE_CHOICE && question.options() != null) {
			return question.options().stream()
				.filter(option -> option.code().equals(answer))
				.map(Option::label)
				.findFirst()
				.orElse(answer);
		}
		return answer;
	}

	private String options(Question question) {
		if (question.options() == null || question.options().isEmpty()) {
			return "";
		}
		return ", 보기 " + question.options().stream()
			.map(option -> option.code() + "=" + option.label())
			.collect(Collectors.joining(", "));
	}

	/**
	 * 질문 형식에 맞는 답만 돌려준다. YES_NO_UNKNOWN은 YES·NO·UNKNOWN, SINGLE_CHOICE는 보기 code여야 한다.
	 */
	private String normalize(Question question, String value) {
		if (value == null) {
			return null;
		}
		return switch (question.answerType()) {
			case YES_NO_UNKNOWN -> List.of("YES", "NO", "UNKNOWN").contains(value) ? value : null;
			case SINGLE_CHOICE -> question.options().stream().anyMatch(option -> option.code().equals(value)) ? value : null;
			case FREE_TEXT -> value;
		};
	}

	private Question question(JsonNode node) {
		String questionId = text(node, "questionId");
		String text = text(node, "text");
		AnswerType answerType = enumOf(AnswerType.class, text(node, "answerType"));
		if (questionId == null || text == null || answerType == null) {
			return null;
		}
		List<Option> options = null;
		if (answerType == AnswerType.SINGLE_CHOICE) {
			options = new ArrayList<>();
			for (JsonNode option : node.path("options")) {
				String code = text(option, "code");
				String label = text(option, "label");
				if (code != null && label != null) {
					options.add(new Option(code, label));
				}
			}
			if (options.size() < 2) {
				return null;
			}
		}
		return new Question(questionId, text, answerType, options);
	}

	private List<String> strings(JsonNode node) {
		List<String> values = new ArrayList<>();
		for (JsonNode value : node) {
			String text = value.isString() ? value.asString().trim() : null;
			if (StringUtils.hasText(text)) {
				values.add(text);
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

	private <E extends Enum<E>> E enumOf(Class<E> type, String value) {
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String enumNames(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
	}

	public record StartResult(boolean understood, String mainSymptom, BodyPart bodyPart, Onset onset,
		String onsetDetail, List<String> collected, Question firstQuestion) {

		static StartResult notUnderstood() {
			return new StartResult(false, null, null, null, null, List.of(), null);
		}
	}

	public record SymptomStep(boolean understood, String normalizedAnswer, List<String> collected, boolean done,
		Question nextQuestion) {

		static SymptomStep notUnderstood() {
			return new SymptomStep(false, null, List.of(), false, null);
		}
	}

	public record DepartmentChoice(String departmentCode, String recommendReason) {
	}
}
