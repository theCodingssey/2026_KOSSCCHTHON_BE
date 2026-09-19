package com.kosscchthon.Icelink.ai;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * icelink.ai.* — 국민대 AI 게이트웨이 설정 (docs/02-api-spec.md 4.4절).
 * apiKey 는 환경 변수 ICELINK_AI_API_KEY 또는 application-local.properties 로만 주입한다.
 */
@Validated
@ConfigurationProperties(prefix = "icelink.ai")
public record AiProperties(
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotBlank String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxTokens,
        Double temperature
) {

    public AiProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(15);
        }
        if (maxTokens == null) {
            maxTokens = 700;
        }
        if (temperature == null) {
            temperature = 0.8;
        }
        baseUrl = baseUrl == null ? null : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
    }
}
