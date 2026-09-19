package com.kosscchthon.Icelink.realtime;

import java.time.Instant;
import java.util.Map;

/**
 * 방/팀에서 발생한 도메인 이벤트. SSE 로 그대로 내려간다.
 * @param teamId 팀 범위 이벤트면 채우고, 방 전체 이벤트면 null
 */
public record RoomEvent(
        Long roomId,
        Long teamId,
        RoomEventType type,
        Map<String, Object> payload,
        Instant occurredAt
) {

    public static RoomEvent room(Long roomId, RoomEventType type, Map<String, Object> payload, Instant occurredAt) {
        return new RoomEvent(roomId, null, type, payload, occurredAt);
    }

    public static RoomEvent team(Long roomId, Long teamId, RoomEventType type, Map<String, Object> payload, Instant occurredAt) {
        return new RoomEvent(roomId, teamId, type, payload, occurredAt);
    }

    public boolean isTeamScoped() {
        return teamId != null;
    }
}
