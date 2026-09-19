package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 방 도메인이 아직 없을 때 쓰는 기본 구현. 항상 "진행 중인 방 없음".
 * room 패키지에서 ActiveRoomProvider 를 구현하면 이 클래스를 삭제한다.
 */
@Component
class NoActiveRoomProvider implements ActiveRoomProvider {

    @Override
    public Optional<ActiveRoomResponse> findActiveRoom(String userKey) {
        return Optional.empty();
    }
}
