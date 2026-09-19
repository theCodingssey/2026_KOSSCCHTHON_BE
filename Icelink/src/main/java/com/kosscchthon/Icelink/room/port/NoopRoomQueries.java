package com.kosscchthon.Icelink.room.port;

import com.kosscchthon.Icelink.room.dto.ParticipantCounts;
import com.kosscchthon.Icelink.room.dto.RoomParticipantSummary;
import com.kosscchthon.Icelink.room.dto.RoomTeamSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * participant / team 모듈이 구현되기 전까지 쓰는 빈 구현.
 * 각 모듈이 실제 구현 빈을 등록하면 여기서 해당 @Bean 을 제거한다.
 */
@Configuration
class NoopRoomQueries {

    @Bean
    RoomParticipantQuery noopRoomParticipantQuery() {
        return new RoomParticipantQuery() {
            @Override
            public int countActive(Long roomId) {
                return 0;
            }

            @Override
            public ParticipantCounts counts(Long roomId) {
                return ParticipantCounts.empty();
            }

            @Override
            public List<RoomParticipantSummary> listActive(Long roomId) {
                return List.of();
            }

            @Override
            public Optional<ActiveParticipation> findActiveParticipation(String userKey) {
                return Optional.empty();
            }
        };
    }

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
