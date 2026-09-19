package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomProperties;
import com.kosscchthon.Icelink.room.RoomStatus;
import java.time.Instant;
import java.util.List;

/** POST /rooms, PATCH /host/rooms/{code} 응답. */
public record RoomResponse(
        Long roomId,
        String code,
        HostSummary host,
        String inviteUrl,
        String deepLink,
        String title,
        String situation,
        int teamSize,
        List<String> finalQuestions,
        RoomStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant finishedAt
) {

    public record HostSummary(String userKey, String name) {
    }

    public static RoomResponse from(Room room, String hostName, RoomProperties props) {
        return new RoomResponse(
                room.getId(),
                room.getCode(),
                new HostSummary(room.getHostUserKey(), hostName),
                props.inviteUrl(room.getCode()),
                props.deepLink(room.getCode()),
                room.getTitle(),
                room.getSituation(),
                room.getTeamSize(),
                room.getFinalQuestions(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getExpiresAt(),
                room.getFinishedAt()
        );
    }
}
