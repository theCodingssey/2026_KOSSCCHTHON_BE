package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** PATCH /host/rooms/{code}. 보낸 필드만 변경. 마무리 질문은 별도 API. */
public record UpdateRoomRequest(
        @Schema(nullable = true) @Size(min = 1, max = Room.TITLE_MAX_LENGTH, message = "제목은 1~50자여야 합니다")
        String title,

        @Schema(nullable = true) @Size(min = 1, max = Room.SITUATION_MAX_LENGTH, message = "상황 설명은 1~500자여야 합니다")
        String situation,

        @Schema(nullable = true) @Min(Room.TEAM_SIZE_MIN) @Max(Room.TEAM_SIZE_MAX)
        Integer teamSize
) {

    public boolean isEmpty() {
        return title == null && situation == null && teamSize == null;
    }
}
