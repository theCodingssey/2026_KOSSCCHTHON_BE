package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.ai.AiClientException;
import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.ai.FallbackQuestionPool;
import com.kosscchthon.Icelink.ai.PromptTemplates;
import com.kosscchthon.Icelink.ai.QuestionAiClient;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionResult;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpResult;
import com.kosscchthon.Icelink.team.session.QuestionGenerationStore.FirstQuestionSnapshot;
import com.kosscchthon.Icelink.team.session.QuestionGenerationStore.FollowUpSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 비동기 AI 파이프라인 (docs/02-api-spec.md 4.3절).
 * 스냅샷 읽기(트랜잭션) → LLM 호출(트랜잭션 밖) → 결과 반영(트랜잭션).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private final QuestionAiClient aiClient;
    private final FallbackQuestionPool fallbackPool;
    private final QuestionGenerationStore store;

    /** 프롬프트 ① — NAMING → QUESTIONING 진입 또는 건너뛰기 뒤 첫/일반 질문 생성. */
    @Async
    public void generateFirstQuestionAsync(Long teamId) {
        try {
            Optional<FirstQuestionSnapshot> snapshot = store.loadFirstQuestionSnapshot(teamId);
            if (snapshot.isEmpty()) {
                log.info("first question generation skipped: team {} not eligible", teamId);
                return;
            }
            Generated g = generateFirst(snapshot.get());
            store.saveGeneratedQuestion(teamId, g.content(), g.type(), g.context());
        } catch (RuntimeException e) {
            log.error("first question generation crashed for team {}", teamId, e);
        }
    }

    /**
     * 프롬프트 ② — 제출된 답변 처리. 키워드 추출 + 꼬리질문 생성.
     * @param retry 재시도면 실패 시 FAILED 로 두지 않고 폴백 질문으로 흐름을 이어간다
     */
    @Async
    public void processAnswerAsync(Long questionId, boolean retry) {
        try {
            Optional<FollowUpSnapshot> snapshot = store.loadFollowUpSnapshot(questionId);
            if (snapshot.isEmpty()) {
                log.info("answer processing skipped: question {} not in PROCESSING", questionId);
                return;
            }
            FollowUpSnapshot s = snapshot.get();
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("prompt", s.tooShort() ? "FIRST_QUESTION(short answer)" : "FOLLOW_UP");
            context.put("category", s.ctx().category().name());
            context.put("accumulatedKeywords", s.ctx().accumulatedKeywords());
            context.put("previousQuestionCount", s.ctx().previousQuestions().size());

            List<String> keywords = List.of();
            String next;
            QuestionType type;
            try {
                if (s.tooShort()) {
                    // Q-16: 답변이 너무 짧으면 ②를 건너뛰고 ①로 일반 질문
                    FirstQuestionResult r = aiClient.generateFirstQuestion(s.ctx().asFirstQuestion());
                    next = r.question();
                    context.put("totalTokens", r.totalTokens());
                } else {
                    FollowUpResult r = aiClient.generateFollowUp(s.ctx());
                    keywords = r.keywords();
                    next = r.nextQuestion();
                    context.put("totalTokens", r.totalTokens());
                }
                type = QuestionType.AI_GENERATED;
            } catch (AiClientException e) {
                log.warn("AI follow-up failed for question {} ({}): {}", questionId, e.reason(), e.getMessage());
                if (!retry) {
                    store.markFailed(questionId, e.reason());
                    return;
                }
                context.put("failureReason", e.reason().name());
                next = null;
                type = QuestionType.FALLBACK;
            }

            if (!isUsable(next, s.existingQuestions())) {
                context.put("fallbackReason", next == null ? "AI_FAILED" : "INVALID_OR_DUPLICATE");
                next = fallbackPool.pick(s.ctx().category(), s.existingQuestions());
                type = QuestionType.FALLBACK;
            }
            store.completeAnswer(questionId, keywords, next, type, context);
        } catch (RuntimeException e) {
            log.error("answer processing crashed for question {}", questionId, e);
            store.markFailed(questionId, AiFailureReason.LLM_ERROR);
        }
    }

    // ---- 내부 ----

    private record Generated(String content, QuestionType type, Map<String, Object> context) {
    }

    private Generated generateFirst(FirstQuestionSnapshot s) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("prompt", "FIRST_QUESTION");
        context.put("category", s.ctx().category().name());
        context.put("memberCount", s.ctx().memberCount());
        String question = null;
        try {
            FirstQuestionResult r = aiClient.generateFirstQuestion(s.ctx());
            question = r.question();
            context.put("totalTokens", r.totalTokens());
        } catch (AiClientException e) {
            log.warn("AI first question failed for team {} ({}): {}", s.teamId(), e.reason(), e.getMessage());
            context.put("failureReason", e.reason().name());
        }
        if (isUsable(question, s.existingQuestions())) {
            return new Generated(question, QuestionType.AI_GENERATED, context);
        }
        context.put("fallbackReason", question == null ? "AI_FAILED" : "INVALID_OR_DUPLICATE");
        return new Generated(fallbackPool.pick(s.ctx().category(), s.existingQuestions()), QuestionType.FALLBACK, context);
    }

    /** 1~200자이며 이 팀에서 이미 나온 질문과 (공백 무시) 같지 않아야 한다 (Q-17). */
    static boolean isUsable(String question, List<String> existing) {
        if (question == null) {
            return false;
        }
        String q = question.trim();
        if (q.isEmpty() || q.length() > PromptTemplates.QUESTION_MAX_LENGTH) {
            return false;
        }
        String norm = FallbackQuestionPool.normalize(q);
        return existing.stream().map(FallbackQuestionPool::normalize).noneMatch(norm::equals);
    }
}
