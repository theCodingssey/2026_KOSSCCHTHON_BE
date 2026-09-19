package com.kosscchthon.Icelink.room.port;

import com.kosscchthon.Icelink.room.dto.RoomTeamSummary;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * team 모듈이 구현되기 전까지 쓰는 빈 구현.
 * RoomParticipantQuery 는 participant 모듈의 JpaRoomParticipantQuery 가 구현한다.
 * team 모듈이 실제 구현 빈을 등록하면 이 클래스를 삭제한다.
 */
@Configuration
class NoopRoomQueries {

    @Bean
    RoomTeamQuery noopRoomTeamQuery() {
        return new RoomTeamQuery() {
            @Override
            public List<RoomTeamSummary> listByRoom(Long roomId) {
                return List.of();
            }

            @Override
            public int finishAll(Long roomId, Instant now) {
                return 0;
            }
        };
    }
}
