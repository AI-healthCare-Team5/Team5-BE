package com.health.jinryohanjang.domain.auth.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * accessToken 쿠키의 회원 ID를 컨트롤러 파라미터(Long)로 받는다.
 * - required = true (토큰 여부 O): 토큰이 없으면 AUTH_401
 * - required = false (토큰 여부 △): 토큰이 없으면 null
 * 토큰이 있는데 만료·위조됐거나 탈퇴한 회원이면 둘 다 AUTH_401이다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {

	boolean required() default true;
}
