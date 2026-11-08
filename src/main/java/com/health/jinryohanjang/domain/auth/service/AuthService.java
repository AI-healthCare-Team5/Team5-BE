package com.health.jinryohanjang.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.domain.auth.client.KakaoOAuthClient;
import com.health.jinryohanjang.domain.auth.client.KakaoOAuthClient.KakaoUser;
import com.health.jinryohanjang.domain.auth.dto.KakaoLoginResponse;
import com.health.jinryohanjang.domain.auth.security.AuthTokenStore;
import com.health.jinryohanjang.domain.auth.security.JwtProvider;
import com.health.jinryohanjang.domain.consent.enums.ConsentType;
import com.health.jinryohanjang.domain.consent.repository.ConsentRepository;
import com.health.jinryohanjang.domain.intake.repository.IntakeStore;
import com.health.jinryohanjang.domain.member.entity.Member;
import com.health.jinryohanjang.domain.member.repository.MemberRepository;
import com.health.jinryohanjang.domain.summary.repository.SummaryStore;

/**
 * 카카오 로그인, 토큰 재발급, 로그아웃을 처리한다.
 */
@Service
public class AuthService {

	private static final int STATE_BYTES = 24;
	private static final int NICKNAME_MAX_LENGTH = 30;

	private final KakaoOAuthClient kakaoOAuthClient;
	private final JwtProvider jwtProvider;
	private final AuthTokenStore authTokenStore;
	private final MemberRepository memberRepository;
	private final ConsentRepository consentRepository;
	private final IntakeStore intakeStore;
	private final SummaryStore summaryStore;
	private final Set<String> allowedRedirectUris;
	private final String recordConsentVersion;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(
		KakaoOAuthClient kakaoOAuthClient,
		JwtProvider jwtProvider,
		AuthTokenStore authTokenStore,
		MemberRepository memberRepository,
		ConsentRepository consentRepository,
		IntakeStore intakeStore,
		SummaryStore summaryStore,
		@Value("${app.kakao.allowed-redirect-uris:}") String allowedRedirectUris,
		@Value("${app.consent.version.record}") String recordConsentVersion) {
		this.kakaoOAuthClient = kakaoOAuthClient;
		this.jwtProvider = jwtProvider;
		this.authTokenStore = authTokenStore;
		this.memberRepository = memberRepository;
		this.consentRepository = consentRepository;
		this.intakeStore = intakeStore;
		this.summaryStore = summaryStore;
		this.allowedRedirectUris = Arrays.stream(allowedRedirectUris.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.collect(Collectors.toUnmodifiableSet());
		this.recordConsentVersion = recordConsentVersion;
	}

	public AuthorizeUrl createAuthorizeUrl(String redirectUri) {
		validateRedirectUri(redirectUri);
		String state = createState();
		return new AuthorizeUrl(kakaoOAuthClient.buildAuthorizeUrl(redirectUri, state), state);
	}

	@Transactional
	public LoginResult login(String code, String state, String savedState, String redirectUri) {
		validateRedirectUri(redirectUri);
		if (!StringUtils.hasText(savedState) || !sameValue(state, savedState)) {
			throw new GeneralException(ErrorStatus.OAUTH_STATE_MISMATCH);
		}
		KakaoUser kakaoUser = kakaoOAuthClient.getUser(code, redirectUri);
		String nickname = trimNickname(kakaoUser.nickname());

		Member member = memberRepository.findByKakaoId(kakaoUser.kakaoId()).orElse(null);
		boolean isNewMember = member == null;
		if (isNewMember) {
			member = memberRepository.save(Member.join(kakaoUser.kakaoId(), nickname));
		} else if (member.isWithdrawn()) {
			member.restore(nickname);
			authTokenStore.clearWithdrawn(member.getId());
		} else {
			member.changeNickname(nickname);
		}

		boolean needsRecordConsent = !consentRepository.existsByMemberIdAndConsentTypeAndPolicyVersionAndWithdrawnAtIsNull(
			member.getId(), ConsentType.RECORD, recordConsentVersion);
		KakaoLoginResponse response = new KakaoLoginResponse(
			member.getId(), member.getNickname(), isNewMember, needsRecordConsent);
		return new LoginResult(response, issueTokens(member.getId()));
	}

	@Transactional(readOnly = true)
	public Tokens refresh(String refreshToken) {
		Long memberId = authTokenStore.findMemberId(refreshToken)
			.orElseThrow(() -> new GeneralException(ErrorStatus.REFRESH_TOKEN_INVALID));
		boolean active = memberRepository.findById(memberId)
			.filter(member -> !member.isWithdrawn())
			.isPresent();
		if (!active || authTokenStore.isWithdrawn(memberId)) {
			authTokenStore.deleteRefreshToken(memberId);
			throw new GeneralException(ErrorStatus.REFRESH_TOKEN_INVALID);
		}
		return issueTokens(memberId);
	}

	/**
	 * 토큰이 없거나 만료여도 성공한다. 유효한 refreshToken과, 공용 기기 대비로 guestKey의 문진·요약지도 지운다.
	 */
	public void logout(String refreshToken, String guestKey) {
		authTokenStore.findMemberId(refreshToken).ifPresent(authTokenStore::deleteRefreshToken);
		if (StringUtils.hasText(guestKey)) {
			intakeStore.deleteAllByGuestKey(guestKey);
			summaryStore.deleteAllByGuestKey(guestKey);
		}
	}

	private Tokens issueTokens(Long memberId) {
		return new Tokens(jwtProvider.createAccessToken(memberId), authTokenStore.issueRefreshToken(memberId));
	}

	private void validateRedirectUri(String redirectUri) {
		if (!allowedRedirectUris.contains(redirectUri)) {
			throw new GeneralException(ErrorStatus.REDIRECT_URI_NOT_ALLOWED);
		}
	}

	private String createState() {
		byte[] random = new byte[STATE_BYTES];
		secureRandom.nextBytes(random);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

	private boolean sameValue(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private String trimNickname(String nickname) {
		if (!StringUtils.hasText(nickname)) {
			return null;
		}
		return nickname.length() > NICKNAME_MAX_LENGTH ? nickname.substring(0, NICKNAME_MAX_LENGTH) : nickname;
	}

	public record AuthorizeUrl(String url, String state) {
	}

	public record Tokens(String accessToken, String refreshToken) {
	}

	public record LoginResult(KakaoLoginResponse response, Tokens tokens) {
	}
}
