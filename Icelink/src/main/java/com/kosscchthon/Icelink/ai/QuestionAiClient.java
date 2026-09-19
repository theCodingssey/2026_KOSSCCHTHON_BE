package com.kosscchthon.Icelink.ai;

import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.List;

/**
 * AI 질문 생성 진입점. 프롬프트가 둘이라 메서드도 둘이다 (docs/02-api-spec.md 4절).
 * 구현체: {@link KookminGatewayAiClient}. 테스트에서는 mock.
 */
public interface QuestionAiClient {

    /** 프롬프트 ① — 카테고리 기반 첫 질문. 평문 질문 1개. */
    FirstQuestionResult generateFirstQuestion(FirstQuestionContext ctx);

    /** 프롬프트 ② — 답변 기반 꼬리질문 + 키워드. */
    FollowUpResult generateFollowUp(FollowUpContext ctx);

    record FirstQuestionContext(String situation, InterestCategory category, int memberCount) {
    }

    record FollowUpContext(
            String situation,
            InterestCategory category,
            int memberCount,
            String currentQuestion,
            String currentAnswerText,
            List<String> accumulatedKeywords,
            List<String> previousQuestions
    ) {
        public FirstQuestionContext asFirstQuestion() {
            return new FirstQuestionContext(situation, category, memberCount);
        }
    }

    /** @param totalTokens 게이트웨이 usage.total_tokens (없으면 0) */
    record FirstQuestionResult(String question, int totalTokens) {
    }

    record FollowUpResult(List<String> keywords, String nextQuestion, int totalTokens) {
    }
}
