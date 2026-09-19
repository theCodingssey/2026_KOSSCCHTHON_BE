package com.kosscchthon.Icelink.common.config;

import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI 에서 X-User-Key 를 한 번 입력하면 모든 요청에 실리도록 설정. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "UserKey";

    @Bean
    public OpenAPI icelinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ice-Link API")
                        .version("v1")
                        .description("아이스브레이킹 앱 Ice-Link 백엔드. 인증은 X-User-Key 헤더 하나로 한다."))
                .components(new Components().addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(UserKeyInterceptor.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
