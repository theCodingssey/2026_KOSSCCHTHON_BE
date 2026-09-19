package com.kosscchthon.Icelink.participant.dto;

import com.kosscchthon.Icelink.participant.Participant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** POST /rooms/{code}/participants. 본문 생략 가능. */
public record JoinRoomRequest(
        @Schema(description = "방 내 닉네임 (1~12자). 생략하면 유저 이름", nullable = true, example = "민수")
        @Size(max = Participant.NICKNAME_MAX_LENGTH, message = "닉네임은 12자 이하여야 합니다")
        String nickname
) {

    public static JoinRoomRequest empty() {
        return new JoinRoomRequest(null);
    }
}
