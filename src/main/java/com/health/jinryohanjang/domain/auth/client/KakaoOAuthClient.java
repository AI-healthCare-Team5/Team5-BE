package com.health.jinryohanjang.domain.auth.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 OAuth 서버와 통신한다 (인가 URL, 토큰 발급, 사용자 정보).
 * client secret은 카카오 콘솔에서 켰을 때만 보낸다.
 */
@Slf4j
@Component
public class KakaoOAuthClient {

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
	private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
	private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
	private static final String INVALID_GRANT = "invalid_grant";
	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;

	public KakaoOAuthClient(
		@Value("${app.kakao.client-id:}") String clientId,
		@Value("${app.kakao.client-secret:}") String clientSecret) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	public String buildAuthorizeUrl(String redirectUri, String state) {
		requireClientId();
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
			.queryParam("client_id", clientId)
			.queryParam("redirect_uri", redirectUri)
			.queryParam("response_type", "code")
			.queryParam("state", state)
			.encode()
			.toUriString();
	}

	public KakaoUser getUser(String code, String redirectUri) {
		requireClientId();
		String accessToken = requestAccessToken(code, redirectUri);
		try {
			KakaoUserResponse user = restClient.get()
				.uri(USER_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(KakaoUserResponse.class);
			if (user == null || user.id() == null) {
				throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
			}
			return new KakaoUser(String.valueOf(user.id()), user.nickname());
		} catch (RestClientException e) {
			log.error("카카오 사용자 정보 조회 실패", e);
			throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
		}
	}

	private String requestAccessToken(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		if (StringUtils.hasText(clientSecret)) {
			form.add("client_secret", clientSecret);
		}
		try {
			KakaoTokenResponse token = restClient.post()
				.uri(TOKEN_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(KakaoTokenResponse.class);
			if (token == null || !StringUtils.hasText(token.accessToken())) {
				throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
			}
			return token.accessToken();
		} catch (HttpClientErrorException e) {
			// code 없음·만료·재사용은 invalid_grant로 온다. 그 외 4xx는 앱 설정 문제라 카카오 실패로 본다.
			if (e.getResponseBodyAsString().contains(INVALID_GRANT)) {
				throw new GeneralException(ErrorStatus.KAKAO_CODE_INVALID);
			}
			log.error("카카오 토큰 발급 실패: {}", e.getResponseBodyAsString());
			throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
		} catch (RestClientException e) {
			log.error("카카오 토큰 발급 실패", e);
			throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
		}
	}

	private void requireClientId() {
		if (!StringUtils.hasText(clientId)) {
			log.error("KAKAO_CLIENT_ID 환경 변수가 없습니다.");
			throw new GeneralException(ErrorStatus.KAKAO_UNAVAILABLE);
		}
	}

	public record KakaoUser(String kakaoId, String nickname) {
	}

	record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	record KakaoUserResponse(
		Long id,
		Properties properties,
		@JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

		String nickname() {
			if (kakaoAccount != null && kakaoAccount.profile() != null
				&& StringUtils.hasText(kakaoAccount.profile().nickname())) {
				return kakaoAccount.profile().nickname();
			}
			return properties == null ? null : properties.nickname();
		}
	}

	record Properties(String nickname) {
	}

	record KakaoAccount(Profile profile) {
	}

	record Profile(String nickname) {
	}
}
