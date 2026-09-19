package com.kosscchthon.Icelink.survey.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.survey.ExtroversionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

/** PUT / GET /rooms/{code}/me/survey 응답. */
public record SurveyResponse(
        ParticipantStatus status,
        boolean personalityDone,
        boolean categoryDone,
        @Schema(nullable = true, description = "6문항 합 (6~30)") Integer extroversionScore,
        @Schema(nullable = true, description = "INTROVERT(6~13) | BALANCED(14~22) | EXTROVERT(23~30)") ExtroversionLevel extroversionLevel,
        @Schema(nullable = true) InterestCategory interestCategory,
        @Schema(description = "문항별 답. 성격 미제출이면 빈 배열") List<SurveySubmitRequest.PersonalityAnswer> personality
) {

    public static SurveyResponse from(Participant p) {
        List<SurveySubmitRequest.PersonalityAnswer> answers = p.getPersonalityAnswers().entrySet().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .map(e -> new SurveySubmitRequest.PersonalityAnswer(e.getKey(), e.getValue()))
                .toList();
        return new SurveyResponse(
                p.getStatus(),
                p.isPersonalityDone(),
                p.isCategoryDone(),
                p.getExtroversionScore(),
                ExtroversionLevel.ofNullable(p.getExtroversionScore()),
                p.getInterestCategory(),
                answers);
    }
}
