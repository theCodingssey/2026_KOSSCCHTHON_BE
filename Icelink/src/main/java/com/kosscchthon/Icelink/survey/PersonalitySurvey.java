package com.kosscchthon.Icelink.survey;

import java.util.Map;

/**
 * 성격 설문 규칙 (S-01, S-02, S-06).
 * 문항 텍스트는 프론트가 갖고 서버는 번호(1~6)와 점수(1~5)만 다룬다.
 * 6문항 모두 외향성을 측정하며 합(6~30)이 외향 점수다.
 */
public final class PersonalitySurvey {

    public static final int QUESTION_COUNT = 6;
    public static final int QUESTION_NO_MIN = 1;
    public static final int QUESTION_NO_MAX = QUESTION_COUNT;
    public static final int SCORE_MIN = 1;
    public static final int SCORE_MAX = 5;
    public static final int TOTAL_MIN = QUESTION_COUNT * SCORE_MIN; // 6
    public static final int TOTAL_MAX = QUESTION_COUNT * SCORE_MAX; // 30

    private PersonalitySurvey() {
    }

    /**
     * 문항 번호 1~6 이 정확히 한 번씩, 점수는 1~5 인지 검증하고 합을 돌려준다.
     * @throws IllegalArgumentException 규칙 위반
     */
    public static int validateAndSum(Map<Integer, Integer> answers) {
        if (answers == null || answers.size() != QUESTION_COUNT) {
            throw new IllegalArgumentException("성격 문항은 정확히 " + QUESTION_COUNT + "개여야 합니다");
        }
        int sum = 0;
        for (int no = QUESTION_NO_MIN; no <= QUESTION_NO_MAX; no++) {
            Integer score = answers.get(no);
            if (score == null) {
                throw new IllegalArgumentException("문항 " + no + " 의 답이 없습니다");
            }
            if (score < SCORE_MIN || score > SCORE_MAX) {
                throw new IllegalArgumentException("문항 " + no + " 의 점수는 " + SCORE_MIN + "~" + SCORE_MAX + " 여야 합니다");
            }
            sum += score;
        }
        return sum;
    }
}
