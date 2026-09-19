package com.kosscchthon.Icelink.realtime;

import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 연결 하나. 어떤 방/팀/참가자 관점인지와 SseEmitter 를 묶는다.
 * @param participantId PARTICIPANT 스코프에서 자기 팀 이벤트 필터링·myTeam 채우기에 사용
 * @param teamId        TEAM 스코프에서 필터링에 사용
 */
public record SseSubscription(
        long id,
        SseScope scope,
        Long roomId,
        Long teamId,
        Long participantId,
        String userKey,
        SseEmitter emitter
) {

    /**
     * 이 구독이 이벤트를 받아야 하는지.
     * @param participantTeams 방의 참가자 → 배정 팀 (PARTICIPANT 스코프 팀 이벤트 판정용)
     */
    public boolean accepts(RoomEventEntity event, Map<Long, Long> participantTeams) {
        if (!roomId.equals(event.getRoomId())) {
            return false;
        }
        return switch (scope) {
            case HOST -> true;
            case PARTICIPANT -> !event.isTeamScoped()
                    || event.getTeamId().equals(participantTeams.get(participantId));
            case TEAM -> (event.isTeamScoped() && event.getTeamId().equals(teamId))
                    || event.getType() == RoomEventType.ROOM_FINISHED;
        };
    }
}
