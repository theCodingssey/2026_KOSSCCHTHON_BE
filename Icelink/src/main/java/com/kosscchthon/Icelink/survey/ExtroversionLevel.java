package com.kosscchthon.Icelink.survey;

/** 외향 점수(6~30) 표시용 구간. 팀 빌딩 계산에는 원점수를 쓴다. */
public enum ExtroversionLevel {
    INTROVERT,   // 6~13
    BALANCED,    // 14~22
    EXTROVERT;   // 23~30

    public static ExtroversionLevel of(int score) {
        if (score <= 13) {
            return INTROVERT;
        }
        if (score <= 22) {
            return BALANCED;
        }
        return EXTROVERT;
    }

    public static ExtroversionLevel ofNullable(Integer score) {
        return score == null ? null : of(score);
    }
}
