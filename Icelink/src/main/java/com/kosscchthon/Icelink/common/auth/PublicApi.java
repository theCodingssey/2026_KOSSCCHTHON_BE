package com.kosscchthon.Icelink.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * X-User-Key 인증을 요구하지 않는 엔드포인트 표시.
 * 컨트롤러 클래스 또는 메서드에 붙인다. (예: POST /users, GET /rooms/{code})
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
