package com.kosscchthon.Icelink.team.session;

/**
 * 질문 상태 (Q-08).
 * ANSWERING ─(텍스트 제출)→ PROCESSING ─(키워드 추출 + 다음 질문 생성)→ DONE
 *     │                        └─(실패)→ FAILED ─(재시도)→ PROCESSING
 *     └─(건너뛰기)→ SKIPPED
 */
public enum QuestionStatus {
    ANSWERING,
    PROCESSING,
    DONE,
    SKIPPED,
    FAILED;

    /** 이 질문에 더 이상 답변·처리가 없을 상태 */
    public boolean isClosed() {
        return this == DONE || this == SKIPPED;
    }
}
