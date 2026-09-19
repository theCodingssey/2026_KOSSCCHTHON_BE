package com.kosscchthon.Icelink.room;

import java.util.EnumSet;
import java.util.Set;

/** 방 상태. WAITING → TEAM_BUILDING → IN_PROGRESS → FINISHED (역행 불가, R-03). */
public enum RoomStatus {
    WAITING,
    TEAM_BUILDING,
    IN_PROGRESS,
    FINISHED;

    /** 진행 중(= 종료되지 않은) 상태 집합. activeRoom 판정과 R-09 에 사용. */
    public static final Set<RoomStatus> ACTIVE = EnumSet.of(WAITING, TEAM_BUILDING, IN_PROGRESS);

    public boolean isActive() {
        return this != FINISHED;
    }

    public boolean canTransitionTo(RoomStatus next) {
        return switch (this) {
            case WAITING -> next == TEAM_BUILDING || next == FINISHED;
            case TEAM_BUILDING -> next == IN_PROGRESS || next == WAITING || next == FINISHED; // WAITING 은 빌딩 실패 롤백
            case IN_PROGRESS -> next == FINISHED;
            case FINISHED -> false;
        };
    }
}
