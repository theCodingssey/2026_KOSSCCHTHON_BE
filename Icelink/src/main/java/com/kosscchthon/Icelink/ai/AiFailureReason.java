package com.kosscchthon.Icelink.ai;

/** 질문 처리 실패 사유. team_questions.failure_reason 과 API 의 failureReason 에 그대로 쓰인다. */
public enum AiFailureReason {
    LLM_TIMEOUT,
    LLM_ERROR,
    LLM_INVALID_RESPONSE
}
