package com.health.jinryohanjang.global.common.response;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러가 속한 도메인
 * 검증 실패(400)나 서버 오류(500)처럼 전역 예외 처리기가 만드는 응답의 code에 쓴다.
 * 모든 컨트롤러 클래스에 붙인다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDomain {

	Domain value();
}
