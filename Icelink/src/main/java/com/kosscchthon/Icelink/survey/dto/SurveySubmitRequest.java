package com.kosscchthon.Icelink.survey.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.survey.PersonalitySurvey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /rooms/{code}/me/survey. 성격·카테고리 둘 다 또는 하나만 보낼 수 있다. 보낸 블록은 전체 덮어쓰기.
 * 둘 다 null 이면 400.
 */
@Schema(description = "설문 제출. personality / interestCategory 중 하나 이상 필수")
public record SurveySubmitRequest(
        @Schema(description = "성격 6문항 답. 보낼 경우 no 1~6 정확히 6개", nullable = true)
        @Size(min = PersonalitySurvey.QUESTION_COUNT, max = PersonalitySurvey.QUESTION_COUNT,
                message = "성격 문항은 정확히 6개여야 합니다")
        List<@Valid PersonalityAnswer> personality,

        @Schema(description = "관심사 카테고리 1개", nullable = true, example = "GAME")
        InterestCategory interestCategory
) {

    public boolean isEmpty() {
        return personality == null && interestCategory == null;
    }

    public record PersonalityAnswer(
            @Schema(description = "문항 번호 1~6", example = "1")
            @NotNull @Min(PersonalitySurvey.QUESTION_NO_MIN) @Max(PersonalitySurvey.QUESTION_NO_MAX)
            Integer no,

            @Schema(description = "점수 1(매우 낮음)~5(매우 높음)", example = "4")
            @NotNull @Min(PersonalitySurvey.SCORE_MIN) @Max(PersonalitySurvey.SCORE_MAX)
            Integer score
    ) {
    }
}
