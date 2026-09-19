package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import java.util.Optional;

/**
 * GET /users/me 의 activeRoom 을 채우는 확장점.
 * 방(room) 도메인이 구현되면 그쪽에서 이 인터페이스의 빈을 제공하고 {@link NoActiveRoomProvider} 는 제거한다.
 */
public interface ActiveRoomProvider {

    /** 유저가 주최 또는 참가 중이며 FINISHED 가 아닌 방 중 가장 최근 것. 없으면 empty. */
    Optional<ActiveRoomResponse> findActiveRoom(String userKey);
}
