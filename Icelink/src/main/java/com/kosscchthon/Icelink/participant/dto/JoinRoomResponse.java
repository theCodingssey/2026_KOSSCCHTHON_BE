package com.kosscchthon.Icelink.participant.dto;

import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.room.Room;

/** POST /rooms/{code}/participants 응답. 201(신규) / 200(이미 참가 중). */
public record JoinRoomResponse(Long participantId, String nickname, ParticipantStatus status, RoomBrief room) {

    public static JoinRoomResponse from(Participant participant, Room room) {
        return new JoinRoomResponse(participant.getId(), participant.getNickname(), participant.getStatus(),
                RoomBrief.from(room));
    }
}
