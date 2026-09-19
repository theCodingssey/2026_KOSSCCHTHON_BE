package com.kosscchthon.Icelink.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final Room ROOM = Room.create("K7M3PQ", User.register("a".repeat(64), "호스트", NOW),
            "t", "s", 4, List.of(), NOW, Duration.ofHours(24));

    @Test
    void create_initialisesDefaultNameAndRoundedAverage() {
        Team team = Team.create(ROOM, 3, InterestCategory.GAME, false, 18.7499);

        assertThat(team.getName()).isEqualTo("3팀");
        assertThat(team.isDefaultName()).isTrue();
        assertThat(team.getExtroversionAvg()).isEqualTo(18.7);
        assertThat(team.getStatus()).isEqualTo(TeamStatus.NOT_STARTED);
        assertThat(team.getQuestionCount()).isZero();
    }

    @Test
    void rename_trimsAndBlankRestoresDefault() {
        Team team = Team.create(ROOM, 1, InterestCategory.GAME, false, 18);

        team.rename("  감자전사 ");
        assertThat(team.getName()).isEqualTo("감자전사");
        assertThat(team.isDefaultName()).isFalse();

        team.rename("");
        assertThat(team.getName()).isEqualTo("1팀");
        assertThat(team.isDefaultName()).isTrue();
    }

    @Test
    void lifecycle_notStarted_naming_questioning_finished() {
        Team team = Team.create(ROOM, 1, InterestCategory.GAME, false, 18);

        assertThat(team.start(NOW)).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.NAMING);
        assertThat(team.getStartedAt()).isEqualTo(NOW);
        assertThat(team.start(NOW.plusSeconds(1))).isFalse(); // 멱등
        assertThat(team.getStartedAt()).isEqualTo(NOW);

        team.beginQuestioning();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.QUESTIONING);
        assertThatThrownBy(team::beginQuestioning).isInstanceOf(IcelinkException.class);

        assertThat(team.finish(NOW.plusSeconds(60))).isTrue();
        assertThat(team.finish(NOW.plusSeconds(61))).isFalse();
        assertThat(team.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> team.rename("x")).isInstanceOf(IcelinkException.class);
    }

    @Test
    void finish_allowedDirectlyFromNotStarted() {
        Team team = Team.create(ROOM, 1, InterestCategory.GAME, false, 18);
        assertThat(team.finish(NOW)).isTrue();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.FINISHED);
    }

    @Test
    void questionLimit() {
        Team team = Team.create(ROOM, 1, InterestCategory.GAME, false, 18);
        for (int i = 0; i < Team.QUESTION_LIMIT; i++) {
            assertThat(team.hasReachedQuestionLimit()).isFalse();
            team.incrementQuestionCount();
        }
        assertThat(team.hasReachedQuestionLimit()).isTrue();
    }
}
