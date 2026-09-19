package com.kosscchthon.Icelink.survey;

import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest.PersonalityAnswer;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** 테스트에서 6문항 답을 간단히 만드는 도우미. */
public final class SurveyTestFixtures {

    private SurveyTestFixtures() {
    }

    /** 6문항 모두 같은 점수 */
    public static Map<Integer, Integer> uniform(int score) {
        return IntStream.rangeClosed(1, 6).boxed()
                .collect(java.util.stream.Collectors.toMap(no -> no, no -> score));
    }

    /** 문항별 점수 6개를 순서대로 */
    public static Map<Integer, Integer> answers(int... scores) {
        if (scores.length != 6) {
            throw new IllegalArgumentException("6 scores required");
        }
        return IntStream.rangeClosed(1, 6).boxed()
                .collect(java.util.stream.Collectors.toMap(no -> no, no -> scores[no - 1]));
    }

    public static List<PersonalityAnswer> answerList(int... scores) {
        return answers(scores).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new PersonalityAnswer(e.getKey(), e.getValue()))
                .toList();
    }
}
