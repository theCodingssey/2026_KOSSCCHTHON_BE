package com.kosscchthon.Icelink.participant.dto;

import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;

/** 참가자 응답에 실리는 방 요약. 상황 설명·주최자 등 민감 정보 없음. */
public record RoomBrief(Long roomId, String code, String title, RoomStatus status, int teamSize) {

    public static RoomBrief from(Room room) {
        return new RoomBrief(room.getId(), room.getCode(), room.getTitle(), room.getStatus(), room.getTeamSize());
    }
}
