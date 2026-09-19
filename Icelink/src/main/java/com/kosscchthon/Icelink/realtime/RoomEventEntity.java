package com.kosscchthon.Icelink.realtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * room_events — 발행된 이벤트의 저장본. id 가 SSE 의 `id`(단조 증가 seq) 이며,
 * 재연결 시 Last-Event-ID 이후 이벤트를 여기서 다시 보낸다 (RT-05).
 */
@Entity
@Table(name = "room_events", indexes = @Index(name = "ix_room_events_room_id", columnList = "room_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    /** 팀 범위 이벤트면 채움, 방 전체 이벤트면 null */
    @Column(name = "team_id", updatable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 40, nullable = false, updatable = false)
    private RoomEventType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RoomEventEntity(RoomEvent event) {
        this.roomId = event.roomId();
        this.teamId = event.teamId();
        this.type = event.type();
        this.payload = event.payload();
        this.createdAt = event.occurredAt();
    }

    public static RoomEventEntity from(RoomEvent event) {
        return new RoomEventEntity(event);
    }

    public boolean isTeamScoped() {
        return teamId != null;
    }

    public RoomEvent toEvent() {
        return new RoomEvent(roomId, teamId, type, payload, createdAt);
    }
}
