package com.health.jinryohanjang.domain.auth.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.WebUtils;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.status.ErrorStatus;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * @LoginMember 파라미터에 accessToken 쿠키의 회원 ID를 넣는다.
 * 필터가 아니라 리졸버에서 확인해서, 실패하면 전역 예외 처리를 거쳐 AUTH_401로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	private final JwtProvider jwtProvider;
	private final AuthTokenStore authTokenStore;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class) && Long.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		Cookie cookie = request == null ? null : WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
		if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
			if (parameter.getParameterAnnotation(LoginMember.class).required()) {
				throw new GeneralException(ErrorStatus.LOGIN_REQUIRED);
			}
			return null;
		}
		Long memberId = jwtProvider.getMemberId(cookie.getValue());
		if (authTokenStore.isWithdrawn(memberId)) {
			throw new GeneralException(ErrorStatus.TOKEN_INVALID);
		}
		return memberId;
	}
}
