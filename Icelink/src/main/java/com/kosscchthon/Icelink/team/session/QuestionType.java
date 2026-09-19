package com.kosscchthon.Icelink.team.session;

/** 질문 출처. INTRO 는 세션 시작 시 고정 문구, FALLBACK 은 AI 실패 시 기본 질문 풀. */
public enum QuestionType {
    INTRO,
    AI_GENERATED,
    FALLBACK
}
