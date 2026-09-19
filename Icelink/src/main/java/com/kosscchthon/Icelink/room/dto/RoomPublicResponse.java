package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** GET /rooms/{code}. 입장 전 코드 확인용, 인증 없음. 민감 정보(상황 설명, 주최자) 없음. */
public record RoomPublicResponse(
        String code,
        String title,
        RoomStatus status,
        int teamSize,
        int participantCount,
        @Schema(description = "WAITING 이고 정원(100) 미만이면 true") boolean joinable
) {

    public static RoomPublicResponse from(Room room, int participantCount) {
        boolean joinable = room.getStatus() == RoomStatus.WAITING && participantCount < Room.PARTICIPANT_LIMIT;
        return new RoomPublicResponse(room.getCode(), room.getTitle(), room.getStatus(), room.getTeamSize(),
                participantCount, joinable);
    }
}
