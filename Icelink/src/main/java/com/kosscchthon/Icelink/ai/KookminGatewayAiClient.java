package com.kosscchthon.Icelink.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 국민대 AI 게이트웨이(OpenAI Chat Completions 호환) 클라이언트. docs/02-api-spec.md 4.4절.
 * - 연결 3초 / 읽기 15초 타임아웃
 * - 5xx·타임아웃은 1회 재시도, 4xx 는 즉시 실패
 * - 프롬프트 ① 은 평문, ② 는 한 줄 JSON 을 파싱
 */
@Slf4j
@Component
public class KookminGatewayAiClient implements QuestionAiClient {

    private static final long RETRY_BACKOFF_MS = 1000;

    private final AiProperties props;
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public KookminGatewayAiClient(AiProperties props) {
        this.props = props;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public FirstQuestionResult generateFirstQuestion(FirstQuestionContext ctx) {
        Completion completion = complete(PromptTemplates.firstQuestion(ctx), false);
        String question = cleanQuestion(completion.content());
        if (question.isEmpty()) {
            throw new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "empty question from gateway");
        }
        return new FirstQuestionResult(question, completion.totalTokens());
    }

    @Override
    public FollowUpResult generateFollowUp(FollowUpContext ctx) {
        Completion completion = complete(PromptTemplates.followUp(ctx), true);
        String body = extractJsonObject(completion.content());
        try {
            JsonNode root = json.readTree(body);
            List<String> keywords = new ArrayList<>();
            JsonNode kw = root.get("keywords");
            if (kw != null && kw.isArray()) {
                for (JsonNode k : kw) {
                    String s = k.asString().trim();
                    if (!s.isEmpty()) {
                        keywords.add(s);
                    }
                }
            }
            JsonNode nq = root.get("nextQuestion");
            String nextQuestion = nq == null ? "" : cleanQuestion(nq.asString());
            if (nextQuestion.isEmpty()) {
                throw new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "nextQuestion missing in gateway JSON");
            }
            return new FollowUpResult(keywords, nextQuestion, completion.totalTokens());
        } catch (JacksonException e) {
            throw new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "gateway content is not JSON: " + abbreviate(body), e);
        }
    }

    // ---- HTTP ----

    private record Completion(String content, int totalTokens) {
    }

    private Completion complete(String prompt, boolean jsonMode) {
        String requestBody = buildRequest(prompt, jsonMode);
        try {
            return call(requestBody);
        } catch (AiClientException first) {
            if (first.reason() == AiFailureReason.LLM_INVALID_RESPONSE || !isRetryable(first)) {
                throw first;
            }
            log.warn("AI gateway call failed ({}), retrying once: {}", first.reason(), first.getMessage());
            sleep(RETRY_BACKOFF_MS);
            return call(requestBody);
        }
    }

    private boolean isRetryable(AiClientException e) {
        return e.reason() == AiFailureReason.LLM_TIMEOUT
                || (e.getCause() instanceof HttpServerErrorException);
    }

    private Completion call(String requestBody) {
        String raw;
        try {
            raw = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException
                    || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));
            throw new AiClientException(timeout ? AiFailureReason.LLM_TIMEOUT : AiFailureReason.LLM_ERROR,
                    "gateway unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new AiClientException(AiFailureReason.LLM_ERROR,
                    "gateway HTTP " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()), e);
        }
        return parseCompletion(raw);
    }

    private String buildRequest(String prompt, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("temperature", props.temperature());
        body.put("max_tokens", props.maxTokens());
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        return json.writeValueAsString(body);
    }

    private Completion parseCompletion(String raw) {
        try {
            JsonNode root = json.readTree(raw);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "no choices in gateway response");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message == null || message.get("content") == null ? "" : message.get("content").asString();
            String finish = choices.get(0).path("finish_reason").asString("");
            if ("length".equals(finish)) {
                log.warn("AI gateway response truncated (finish_reason=length, max_tokens={})", props.maxTokens());
            }
            int total = root.path("usage").path("total_tokens").asInt(0);
            return new Completion(content, total);
        } catch (JacksonException e) {
            throw new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "gateway response is not JSON", e);
        }
    }

    // ---- 텍스트 정리 ----

    /** 앞뒤 공백·따옴표·번호·"질문:" 접두어 제거, 200자 제한. */
    static String cleanQuestion(String raw) {
        if (raw == null) {
            return "";
        }
        String q = raw.trim();
        q = q.replaceAll("^(질문\\s*[:：]\\s*)", "");
        q = q.replaceAll("^\\d+[.)]\\s*", "");
        q = q.replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "").trim();
        if (q.length() > PromptTemplates.QUESTION_MAX_LENGTH) {
            q = q.substring(0, PromptTemplates.QUESTION_MAX_LENGTH).trim();
        }
        return q;
    }

    /** 응답에 설명이 섞여 와도 첫 '{' ~ 마지막 '}' 만 취한다. */
    static String extractJsonObject(String content) {
        if (content == null) {
            return "";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return content.trim();
        }
        return content.substring(start, end + 1);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
