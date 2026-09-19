package com.kosscchthon.Icelink.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalitySurveyTest {

    @Test
    void validateAndSum_returnsTotalForSixValidAnswers() {
        assertThat(PersonalitySurvey.validateAndSum(SurveyTestFixtures.answers(4, 5, 3, 4, 5, 3))).isEqualTo(24);
        assertThat(PersonalitySurvey.validateAndSum(SurveyTestFixtures.uniform(1))).isEqualTo(6);
        assertThat(PersonalitySurvey.validateAndSum(SurveyTestFixtures.uniform(5))).isEqualTo(30);
    }

    @Test
    void validateAndSum_rejectsWrongCountMissingNumberAndOutOfRangeScore() {
        assertThatThrownBy(() -> PersonalitySurvey.validateAndSum(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersonalitySurvey.validateAndSum(Map.of(1, 3))).isInstanceOf(IllegalArgumentException.class);

        Map<Integer, Integer> shifted = new HashMap<>(SurveyTestFixtures.uniform(3));
        shifted.remove(6);
        shifted.put(7, 3);
        assertThatThrownBy(() -> PersonalitySurvey.validateAndSum(shifted))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("문항 6");

        assertThatThrownBy(() -> PersonalitySurvey.validateAndSum(SurveyTestFixtures.answers(0, 3, 3, 3, 3, 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersonalitySurvey.validateAndSum(SurveyTestFixtures.answers(3, 3, 3, 3, 3, 6)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extroversionLevel_boundaries() {
        assertThat(ExtroversionLevel.of(6)).isEqualTo(ExtroversionLevel.INTROVERT);
        assertThat(ExtroversionLevel.of(13)).isEqualTo(ExtroversionLevel.INTROVERT);
        assertThat(ExtroversionLevel.of(14)).isEqualTo(ExtroversionLevel.BALANCED);
        assertThat(ExtroversionLevel.of(22)).isEqualTo(ExtroversionLevel.BALANCED);
        assertThat(ExtroversionLevel.of(23)).isEqualTo(ExtroversionLevel.EXTROVERT);
        assertThat(ExtroversionLevel.of(30)).isEqualTo(ExtroversionLevel.EXTROVERT);
        assertThat(ExtroversionLevel.ofNullable(null)).isNull();
    }
}
