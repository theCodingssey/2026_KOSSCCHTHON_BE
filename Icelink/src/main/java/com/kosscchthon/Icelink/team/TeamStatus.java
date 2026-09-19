package com.kosscchthon.Icelink.team;

/**
 * 팀 세션 상태 (Q-14). NOT_STARTED → NAMING → QUESTIONING → FINISHED.
 * FINISHED 전이는 방 종료로만 일어난다.
 */
public enum TeamStatus {
    NOT_STARTED,
    NAMING,
    QUESTIONING,
    FINISHED;

    public boolean isFinished() {
        return this == FINISHED;
    }

    public boolean canTransitionTo(TeamStatus next) {
        return switch (this) {
            case NOT_STARTED -> next == NAMING || next == FINISHED;
            case NAMING -> next == QUESTIONING || next == FINISHED;
            case QUESTIONING -> next == FINISHED;
            case FINISHED -> false;
        };
    }
}
