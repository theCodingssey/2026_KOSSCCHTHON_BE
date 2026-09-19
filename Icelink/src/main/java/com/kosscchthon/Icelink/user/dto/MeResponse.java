package com.kosscchthon.Icelink.user.dto;

import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "내 정보 + 진행 중인 방. 앱 기동 시 첫 화면을 결정하는 응답")
public record MeResponse(
        String userKey,
        String name,
        Instant createdAt,
        @Schema(nullable = true, description = "진행 중인 방이 없으면 null → 홈 화면") ActiveRoomResponse activeRoom
) {

    public static MeResponse of(User user, ActiveRoomResponse activeRoom) {
        return new MeResponse(user.getUserKey(), user.getName(), user.getCreatedAt(), activeRoom);
    }
}
