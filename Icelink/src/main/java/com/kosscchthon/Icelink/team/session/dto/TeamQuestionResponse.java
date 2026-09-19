package com.kosscchthon.Icelink.team.session.dto;

import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.team.session.QuestionStatus;
import com.kosscchthon.Icelink.team.session.QuestionType;
import com.kosscchthon.Icelink.team.session.TeamAnswer;
import com.kosscchthon.Icelink.team.session.TeamQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 질문 이력 원소 / 현재 질문 / 단건 조회 응답. */
public record TeamQuestionResponse(
        Long questionId,
        int orderNo,
        QuestionType type,
        String content,
        QuestionStatus status,
        @Schema(nullable = true, description = "FAILED 일 때만") AiFailureReason failureReason,
        @Schema(nullable = true, description = "FAILED 일 때만. 답변이 있으면 true") Boolean retryable,
        @Schema(nullable = true) AnswerView answer,
        Instant createdAt
) {

    public record AnswerView(
            String answerText,
            SubmitterView submittedBy,
            @Schema(nullable = true) Integer speechDurationSec,
            List<String> keywords,
            Instant submittedAt,
            @Schema(nullable = true) Instant processedAt
    ) {
        public record SubmitterView(Long participantId, String nickname) {
        }

        public static AnswerView from(TeamAnswer a) {
            return new AnswerView(a.getAnswerText(),
                    new SubmitterView(a.getSubmittedBy().getId(), a.getSubmittedBy().getNickname()),
                    a.getSpeechDurationSec(), a.getKeywords(), a.getSubmittedAt(), a.getProcessedAt());
        }
    }

    public static TeamQuestionResponse from(TeamQuestion q, TeamAnswer answer) {
        boolean failed = q.getStatus() == QuestionStatus.FAILED;
        return new TeamQuestionResponse(
                q.getId(), q.getOrderNo(), q.getType(), q.getContent(), q.getStatus(),
                failed ? q.getFailureReason() : null,
                failed ? answer != null : null,
                answer == null ? null : AnswerView.from(answer),
                q.getCreatedAt());
    }
}
