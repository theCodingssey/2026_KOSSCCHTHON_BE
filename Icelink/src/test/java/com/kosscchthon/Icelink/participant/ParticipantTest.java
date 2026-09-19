package com.kosscchthon.Icelink.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipantTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);
    private static final User GUEST = User.register("b".repeat(64), "민수", NOW);
    private static final Room ROOM = Room.create("K7M3PQ", HOST, "t", "s", 4, List.of(), NOW, Duration.ofHours(24));

    @Test
    void join_startsJoinedWithoutSurvey() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);

        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.JOINED);
        assertThat(p.isActive()).isTrue();
        assertThat(p.isSurveyDone()).isFalse();
        assertThat(p.getUserKey()).isEqualTo(GUEST.getUserKey());
    }

    @Test
    void survey_becomesSurveyDoneOnlyWhenBothPartsPresent() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);

        p.submitPersonality(24);
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.JOINED);
        assertThat(p.isPersonalityDone()).isTrue();

        p.selectCategory(InterestCategory.GAME);
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.SURVEY_DONE);
        assertThat(p.isSurveyDone()).isTrue();
    }

    @Test
    void submitPersonality_rejectsOutOfRange() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);

        assertThatThrownBy(() -> p.submitPersonality(5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.submitPersonality(31)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leave_allowedBeforeAssignment_thenRejoinResetsSurvey() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);
        p.submitPersonality(20);
        p.selectCategory(InterestCategory.FOOD);

        p.leave(NOW.plusSeconds(10));
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(p.getLeftAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(p.isActive()).isFalse();

        p.rejoin("민수2", NOW.plusSeconds(20));
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.JOINED);
        assertThat(p.getNickname()).isEqualTo("민수2");
        assertThat(p.getJoinedAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(p.getLeftAt()).isNull();
        assertThat(p.isSurveyDone()).isFalse();
    }

    @Test
    void leave_rejectedAfterAssignment_andSurveyLockedAfterAssignment() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);
        p.submitPersonality(20);
        p.selectCategory(InterestCategory.FOOD);
        p.assign();
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.ASSIGNED);

        assertThatThrownBy(() -> p.leave(NOW))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> p.selectCategory(InterestCategory.GAME))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void markLate_onlyFromJoined() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);
        p.markLate();
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.LATE);

        Participant done = Participant.join(ROOM, GUEST, "민수", NOW);
        done.submitPersonality(20);
        done.selectCategory(InterestCategory.FOOD);
        assertThatThrownBy(done::markLate).isInstanceOf(IcelinkException.class);
    }

    @Test
    void rejoin_onActiveParticipantIsProgrammingError() {
        Participant p = Participant.join(ROOM, GUEST, "민수", NOW);
        assertThatThrownBy(() -> p.rejoin("x", NOW)).isInstanceOf(IllegalStateException.class);
    }
}
