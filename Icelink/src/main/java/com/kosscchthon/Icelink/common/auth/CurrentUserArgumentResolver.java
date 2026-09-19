package com.kosscchthon.Icelink.common.auth;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.User;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@code @CurrentUser User} 파라미터에 인터셉터가 넣어둔 User 를 주입한다. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(UserKeyInterceptor.ATTR_CURRENT_USER, RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            // @PublicApi 핸들러에서 @CurrentUser 를 쓰면 여기로 온다. 설계 오류이므로 명확히 실패시킨다.
            throw new IcelinkException(ErrorCode.USER_KEY_REQUIRED, "인증이 필요한 요청입니다.");
        }
        return user;
    }
}
