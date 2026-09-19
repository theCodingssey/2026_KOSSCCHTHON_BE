package com.kosscchthon.Icelink.realtime;

/** SSE 이벤트 타입. docs/02-api-spec.md 3.3절과 1:1. */
public enum RoomEventType {
    // 방 범위
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    PARTICIPANT_SURVEY_DONE,
    ROOM_UPDATED,
    TEAM_BUILDING_STARTED,
    TEAM_BUILDING_COMPLETED,
    ROOM_FINISHED,
    TEAM_STATUS_CHANGED,
    TEAM_FINISHED,
    // 팀 범위 (방 스트림에도 일부 전달)
    TEAM_STARTED,
    TEAM_NAME_CHANGED,
    QUESTION_GENERATING,
    QUESTION_CREATED,
    ANSWER_PROCESSING,
    ANSWER_PROCESSED,
    ANSWER_FAILED
}
