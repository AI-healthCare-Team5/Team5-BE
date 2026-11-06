package com.health.jinryohanjang.common.util;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bedrock Converse API로 LLM(Claude)을 부른다. 모델 ID와 리전은 환경 변수로 받는다.
 * 한 번 시도에 10초까지 기다리고, 실패하면 1번 재시도한 뒤 AiUnavailableException을 던진다.
 */
@Slf4j
@Component
public class BedrockChatClient {

	private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(10);
	private static final int MAX_ATTEMPTS = 2;

	private final BedrockRuntimeClient client;
	private final String modelId;
	private final ObjectMapper objectMapper;

	public BedrockChatClient(
		@Value("${app.bedrock.region}") String region,
		@Value("${app.bedrock.model-id}") String modelId,
		@Value("${app.bedrock.profile:}") String profile,
		ObjectMapper objectMapper) {
		AwsCredentialsProvider credentials = StringUtils.hasText(profile)
			? ProfileCredentialsProvider.builder().profileName(profile).build()
			: DefaultCredentialsProvider.builder().build();
		this.client = BedrockRuntimeClient.builder()
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.overrideConfiguration(config -> config
				.apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
				.retryStrategy(retry -> retry.maxAttempts(MAX_ATTEMPTS)))
			.build();
		this.modelId = modelId;
		this.objectMapper = objectMapper;
	}

	/**
	 * 응답을 JSON 객체로 받는다. 코드 블록(```json)이 섞여도 첫 { 부터 마지막 } 까지만 읽는다.
	 */
	public JsonNode askJson(String systemPrompt, String userPrompt, int maxTokens) {
		String text = ask(systemPrompt, userPrompt, maxTokens);
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			log.warn("LLM 응답이 JSON이 아닙니다: {}", text.length() > 200 ? text.substring(0, 200) : text);
			throw new AiUnavailableException("LLM 응답이 JSON이 아닙니다.");
		}
		try {
			return objectMapper.readTree(text.substring(start, end + 1));
		} catch (JacksonException e) {
			log.warn("LLM 응답 JSON을 읽지 못했습니다.", e);
			throw new AiUnavailableException("LLM 응답 JSON을 읽지 못했습니다.");
		}
	}

	private String ask(String systemPrompt, String userPrompt, int maxTokens) {
		try {
			ConverseResponse response = client.converse(request -> request
				.modelId(modelId)
				.system(SystemContentBlock.fromText(systemPrompt))
				.messages(Message.builder()
					.role(ConversationRole.USER)
					.content(ContentBlock.fromText(userPrompt))
					.build())
				// Claude Haiku 5.5는 temperature를 받지 않습니다(ValidationException 400).
				.inferenceConfig(config -> config.maxTokens(maxTokens)));
			return response.output().message().content().stream()
				.map(ContentBlock::text)
				.filter(StringUtils::hasText)
				.findFirst()
				.orElseThrow(() -> new AiUnavailableException("LLM 응답이 비어 있습니다."));
		} catch (SdkException e) {
			log.error("Bedrock 호출 실패", e);
			throw new AiUnavailableException("Bedrock 호출 실패");
		}
	}

	public static class AiUnavailableException extends RuntimeException {

		public AiUnavailableException(String message) {
			super(message);
		}
	}
}
