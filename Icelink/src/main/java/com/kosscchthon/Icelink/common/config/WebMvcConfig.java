package com.kosscchthon.Icelink.common.config;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String API_BASE = "/api/v1";

    private final UserKeyInterceptor userKeyInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    /**
     * CORS 허용 오리진. Flutter 모바일 앱은 CORS 와 무관하고, Flutter 웹 빌드(아이폰 참가자용)·Swagger 에서 필요하다.
     * 해커톤 기본값은 전체 허용. 운영에서 제한하려면 icelink.cors.allowed-origins 에 콤마 구분으로 나열.
     */
    @Value("${icelink.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userKeyInterceptor)
                .addPathPatterns(API_BASE + "/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "Location")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
