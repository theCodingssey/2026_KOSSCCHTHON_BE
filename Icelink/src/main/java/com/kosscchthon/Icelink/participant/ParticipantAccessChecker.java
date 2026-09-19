package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 참가자 역할 판정: participants(room, user) 행이 있고 LEFT 가 아니어야 한다 (0.1절). */
@Component
@RequiredArgsConstructor
public class ParticipantAccessChecker {

    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantRepository participantRepository;

    /** 방 코드로 방을 찾고, 요청 유저의 활성 참가 행을 돌려준다. 없으면 403. */
    public Participant requireParticipant(String rawCode, String userKey) {
        Room room = roomAccessChecker.getByCode(rawCode);
        return requireParticipant(room, userKey);
    }

    public Participant requireParticipant(Room room, String userKey) {
        return participantRepository.findByRoom_IdAndUser_UserKey(room.getId(), userKey)
                .filter(Participant::isActive)
                .orElseThrow(() -> new IcelinkException(ErrorCode.FORBIDDEN, "이 방의 참가자가 아닙니다."));
    }
}
