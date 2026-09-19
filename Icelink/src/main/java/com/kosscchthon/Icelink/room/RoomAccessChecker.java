package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 방 단위 역할 판정 (docs/02-api-spec.md 0.1절).
 * 인터셉터는 "누구인지"만 확인하고, "이 방에서 무엇인지"는 여기서 판정한다.
 * 참가자/팀원 판정은 participant/team 모듈에서 이 클래스를 확장하거나 별도 체커로 제공한다.
 */
@Component
@RequiredArgsConstructor
public class RoomAccessChecker {

    private final RoomRepository roomRepository;

    /** 코드로 방을 찾는다. 없으면 404. 소문자 입력도 허용. */
    public Room getByCode(String rawCode) {
        String code = RoomCodeGenerator.normalize(rawCode);
        return roomRepository.findByCode(code)
                .orElseThrow(() -> new IcelinkException(ErrorCode.ROOM_NOT_FOUND, "존재하지 않는 방 코드입니다: " + code));
    }

    /** 방을 찾고 요청 유저가 주최자인지 확인한다. 아니면 403. */
    public Room requireHost(String rawCode, String userKey) {
        Room room = getByCode(rawCode);
        requireHost(room, userKey);
        return room;
    }

    public void requireHost(Room room, String userKey) {
        if (!room.isHostedBy(userKey)) {
            throw new IcelinkException(ErrorCode.FORBIDDEN, "이 방의 주최자만 할 수 있는 요청입니다.");
        }
    }
}
