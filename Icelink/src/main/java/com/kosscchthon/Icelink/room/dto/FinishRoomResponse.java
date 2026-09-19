package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.RoomStatus;
import java.time.Instant;
import java.util.List;

/** POST /host/rooms/{code}/finish 응답. */
public record FinishRoomResponse(
        RoomStatus roomStatus,
        Instant finishedAt,
        int finishedTeamCount,
        List<String> finalQuestions
) {
}
