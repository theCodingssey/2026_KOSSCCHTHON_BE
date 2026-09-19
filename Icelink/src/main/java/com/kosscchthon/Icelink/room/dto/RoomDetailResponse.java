package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomProperties;
import com.kosscchthon.Icelink.room.RoomStatus;
import java.time.Instant;
import java.util.List;

/** GET /host/rooms/{code}. 주최자 대시보드 한 화면에 필요한 모든 것. */
public record RoomDetailResponse(
        Long roomId,
        String code,
        String title,
        String situation,
        int teamSize,
        List<String> finalQuestions,
        RoomStatus status,
        String inviteUrl,
        String deepLink,
        ParticipantCounts counts,
        List<RoomParticipantSummary> participants,
        List<RoomTeamSummary> teams,
        Instant createdAt,
        Instant expiresAt,
        Instant finishedAt
) {

    public static RoomDetailResponse from(Room room, RoomProperties props, ParticipantCounts counts,
                                          List<RoomParticipantSummary> participants, List<RoomTeamSummary> teams) {
        return new RoomDetailResponse(
                room.getId(),
                room.getCode(),
                room.getTitle(),
                room.getSituation(),
                room.getTeamSize(),
                room.getFinalQuestions(),
                room.getStatus(),
                props.inviteUrl(room.getCode()),
                props.deepLink(room.getCode()),
                counts,
                participants,
                teams,
                room.getCreatedAt(),
                room.getExpiresAt(),
                room.getFinishedAt()
        );
    }
}
