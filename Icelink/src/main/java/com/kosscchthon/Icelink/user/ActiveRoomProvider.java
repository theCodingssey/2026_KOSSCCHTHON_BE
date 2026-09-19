package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import java.util.Optional;

/**
 * GET /users/me 의 activeRoom 을 채우는 확장점.
 * 구현: room 패키지의 RoomActiveRoomProvider.
 */
public interface ActiveRoomProvider {

    /** 유저가 주최 또는 참가 중이며 FINISHED 가 아닌 방 중 가장 최근 것. 없으면 empty. */
    Optional<ActiveRoomResponse> findActiveRoom(String userKey);
}
