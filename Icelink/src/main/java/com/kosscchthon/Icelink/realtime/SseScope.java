package com.kosscchthon.Icelink.realtime;

/** SSE 스트림 종류 (docs 3.1절). 어떤 이벤트를 받을지 결정한다. */
public enum SseScope {
    /** 방 전체 + 모든 팀 이벤트 */
    HOST,
    /** 방 이벤트 + 자기 팀 이벤트 (팀은 구독 이후 배정될 수 있어 이벤트마다 다시 확인) */
    PARTICIPANT,
    /** 해당 팀 이벤트 + ROOM_FINISHED */
    TEAM
}
