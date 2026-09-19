package com.kosscchthon.Icelink.participant;

/**
 * 참가자 상태 (P-05).
 * JOINED → SURVEY_DONE → ASSIGNED. 팀 빌딩 마감 시 미완료자는 LATE. 나가기/강퇴는 LEFT.
 */
public enum ParticipantStatus {
    JOINED,
    SURVEY_DONE,
    ASSIGNED,
    LATE,
    LEFT;

    public boolean isActive() {
        return this != LEFT;
    }

    /** 팀 빌딩 전, 방에서 나갈 수 있는 상태 */
    public boolean canLeave() {
        return this == JOINED || this == SURVEY_DONE;
    }
}
