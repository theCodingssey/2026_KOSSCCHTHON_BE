package com.kosscchthon.Icelink.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "민수", NOW);

    private static Room room() {
        return Room.create("K7M3PQ", HOST, "제목", "상황", 4, List.of("q1"), NOW, Duration.ofHours(24));
    }

    @Test
    void create_startsWaitingWithExpiry() {
        Room room = room();

        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(room.isHostedBy(HOST.getUserKey())).isTrue();
        assertThat(room.isHostedBy("b".repeat(64))).isFalse();
        assertThat(room.getFinalQuestions()).containsExactly("q1");
    }

    @Test
    void updateSettings_onlyInWaiting_andKeepsNullFields() {
        Room room = room();
        room.updateSettings(null, "새 상황", 6, NOW.plusSeconds(1));

        assertThat(room.getTitle()).isEqualTo("제목");
        assertThat(room.getSituation()).isEqualTo("새 상황");
        assertThat(room.getTeamSize()).isEqualTo(6);

        room.startTeamBuilding(NOW.plusSeconds(2));
        assertThatThrownBy(() -> room.updateSettings("x", null, null, NOW))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void finalQuestions_editableUntilFinished() {
        Room room = room();
        room.startTeamBuilding(NOW);
        room.completeTeamBuilding(NOW);
        room.replaceFinalQuestions(List.of("a", "b"), NOW);
        assertThat(room.getFinalQuestions()).containsExactly("a", "b");

        room.finish(NOW);
        assertThatThrownBy(() -> room.replaceFinalQuestions(List.of("c"), NOW))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void statusTransitions_followForwardOnlyPath() {
        Room room = room();
        room.startTeamBuilding(NOW);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.TEAM_BUILDING);
        room.rollbackTeamBuilding(NOW);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        room.startTeamBuilding(NOW);
        room.completeTeamBuilding(NOW);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);

        assertThatThrownBy(() -> room.startTeamBuilding(NOW))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void finish_isIdempotentAndAllowedFromAnyActiveState() {
        Room waiting = room();
        assertThat(waiting.finish(NOW)).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(waiting.getFinishedAt()).isEqualTo(NOW);

        assertThat(waiting.finish(NOW.plusSeconds(10))).isFalse();
        assertThat(waiting.getFinishedAt()).isEqualTo(NOW);
        assertThat(waiting.isActive()).isFalse();
    }
}
