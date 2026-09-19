package com.kosscchthon.Icelink.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseSubscriptionTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    static RoomEventEntity event(long id, Long roomId, Long teamId, RoomEventType type) {
        RoomEventEntity e = RoomEventEntity.from(new RoomEvent(roomId, teamId, type, Map.of(), NOW));
        try {
            Field f = RoomEventEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    private static SseSubscription sub(SseScope scope, Long roomId, Long teamId, Long participantId) {
        return new SseSubscription(1, scope, roomId, teamId, participantId, "k", new SseEmitter());
    }

    @Test
    void host_receivesEverythingInItsRoomOnly() {
        SseSubscription host = sub(SseScope.HOST, 12L, null, null);

        assertThat(host.accepts(event(1, 12L, null, RoomEventType.PARTICIPANT_JOINED), Map.of())).isTrue();
        assertThat(host.accepts(event(2, 12L, 501L, RoomEventType.QUESTION_CREATED), Map.of())).isTrue();
        assertThat(host.accepts(event(3, 99L, null, RoomEventType.PARTICIPANT_JOINED), Map.of())).isFalse();
    }

    @Test
    void participant_receivesRoomEvents_andOnlyOwnTeamEvents() {
        SseSubscription p = sub(SseScope.PARTICIPANT, 12L, null, 101L);
        Map<Long, Long> teams = Map.of(101L, 501L, 102L, 502L);

        assertThat(p.accepts(event(1, 12L, null, RoomEventType.TEAM_BUILDING_COMPLETED), Map.of())).isTrue();
        assertThat(p.accepts(event(2, 12L, 501L, RoomEventType.QUESTION_CREATED), teams)).isTrue();
        assertThat(p.accepts(event(3, 12L, 502L, RoomEventType.QUESTION_CREATED), teams)).isFalse();
        // 아직 팀 배정 전이면 팀 이벤트는 받지 않는다
        assertThat(p.accepts(event(4, 12L, 501L, RoomEventType.TEAM_STARTED), Map.of())).isFalse();
    }

    @Test
    void team_receivesOwnTeamEvents_andRoomFinished() {
        SseSubscription t = sub(SseScope.TEAM, 12L, 501L, 101L);

        assertThat(t.accepts(event(1, 12L, 501L, RoomEventType.ANSWER_PROCESSED), Map.of())).isTrue();
        assertThat(t.accepts(event(2, 12L, 502L, RoomEventType.ANSWER_PROCESSED), Map.of())).isFalse();
        assertThat(t.accepts(event(3, 12L, null, RoomEventType.ROOM_FINISHED), Map.of())).isTrue();
        assertThat(t.accepts(event(4, 12L, null, RoomEventType.PARTICIPANT_JOINED), Map.of())).isFalse();
    }
}
