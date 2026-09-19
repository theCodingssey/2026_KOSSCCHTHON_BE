package com.kosscchthon.Icelink.common.auth;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserKeys;
import com.kosscchthon.Icelink.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 /api/v1/** 요청에서 X-User-Key 를 읽어 유저를 확인한다.
 * - @PublicApi 가 붙은 핸들러는 건너뛴다.
 * - 헤더가 없으면 SSE 용으로 ?userKey= 쿼리도 허용한다.
 * - 성공 시 request attribute 에 User 를 넣고 last_seen_at 을 (스로틀링해서) 갱신한다.
 *
 * 역할(호스트/참가자/팀원) 판정은 여기서 하지 않는다. 방·팀을 로딩하는 서비스 계층에서 한다.
 */
@Component
@RequiredArgsConstructor
public class UserKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-User-Key";
    public static final String QUERY_PARAM = "userKey";
    public static final String ATTR_CURRENT_USER = UserKeyInterceptor.class.getName() + ".currentUser";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isPublic(handlerMethod)) {
            return true;
        }

        String key = resolveKey(request);
        if (key == null || key.isBlank()) {
            throw new IcelinkException(ErrorCode.USER_KEY_REQUIRED, "X-User-Key 헤더가 필요합니다.");
        }
        if (!UserKeys.isValidFormat(key)) {
            throw new IcelinkException(ErrorCode.USER_KEY_INVALID_FORMAT, "유저 키는 64자 소문자 hex 여야 합니다.");
        }

        User user = userService.authenticate(key)
                .orElseThrow(() -> new IcelinkException(ErrorCode.USER_NOT_FOUND, "등록되지 않은 유저 키입니다."));
        request.setAttribute(ATTR_CURRENT_USER, user);
        return true;
    }

    private static boolean isPublic(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
    }

    private static String resolveKey(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String query = request.getParameter(QUERY_PARAM);
        return query == null ? null : query.trim();
    }
}
