package com.kosscchthon.Icelink.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantAccessChecker;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomRepository;
import com.kosscchthon.Icelink.survey.dto.SurveyResponse;
import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest;
import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest.PersonalityAnswer;
import com.kosscchthon.Icelink.user.User;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);
    private static final User GUEST = User.register("b".repeat(64), "민수", NOW);

    @Mock RoomRepository roomRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock RoomEventPublisher eventPublisher;

    private SurveyService service;
    private Room room;
    private Participant me;

    @BeforeEach
    void setUp() throws Exception {
        RoomAccessChecker roomAccess = new RoomAccessChecker(roomRepository);
        service = new SurveyService(roomAccess, new ParticipantAccessChecker(roomAccess, participantRepository),
                participantRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

        room = Room.create("K7M3PQ", HOST, "t", "s", 4, List.of(), NOW, Duration.ofHours(24));
        setId(room, Room.class, 12L);
        me = Participant.join(room, GUEST, "민수", NOW);
        setId(me, Participant.class, 101L);

        lenient().when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
        lenient().when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(me));
    }

    private static void setId(Object entity, Class<?> type, long id) throws Exception {
        Field f = type.getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    @Test
    void submitPersonalityOnly_storesScoreButStaysJoined_noEvent() {
        SurveyResponse res = service.submit("K7M3PQ", GUEST,
                new SurveySubmitRequest(SurveyTestFixtures.answerList(4, 5, 3, 4, 5, 3), null));

        assertThat(res.status()).isEqualTo(ParticipantStatus.JOINED);
        assertThat(res.personalityDone()).isTrue();
        assertThat(res.categoryDone()).isFalse();
        assertThat(res.extroversionScore()).isEqualTo(24);
        assertThat(res.extroversionLevel()).isEqualTo(ExtroversionLevel.EXTROVERT);
        assertThat(res.personality()).hasSize(6);
        assertThat(res.personality().get(1)).isEqualTo(new PersonalityAnswer(2, 5));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void submitCategoryThenPersonality_becomesSurveyDone_andPublishesOnce() {
        when(participantRepository.countByRoom_IdAndStatus(12L, ParticipantStatus.SURVEY_DONE)).thenReturn(3);
        when(participantRepository.countByRoom_IdAndStatusNot(12L, ParticipantStatus.LEFT)).thenReturn(8);

        service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(null, InterestCategory.GAME));
        verify(eventPublisher, never()).publish(any());

        SurveyResponse res = service.submit("K7M3PQ", GUEST,
                new SurveySubmitRequest(SurveyTestFixtures.answerList(2, 2, 2, 2, 2, 2), null));

        assertThat(res.status()).isEqualTo(ParticipantStatus.SURVEY_DONE);
        assertThat(res.interestCategory()).isEqualTo(InterestCategory.GAME);
        assertThat(res.extroversionLevel()).isEqualTo(ExtroversionLevel.INTROVERT);

        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RoomEventType.PARTICIPANT_SURVEY_DONE);
        assertThat(captor.getValue().payload())
                .containsEntry("participantId", 101L)
                .containsEntry("surveyDoneCount", 3)
                .containsEntry("participantCount", 8);
    }

    @Test
    void resubmitAfterDone_overwritesWithoutSecondEvent() {
        when(participantRepository.countByRoom_IdAndStatus(any(), any())).thenReturn(1);
        when(participantRepository.countByRoom_IdAndStatusNot(any(), any())).thenReturn(1);
        service.submit("K7M3PQ", GUEST,
                new SurveySubmitRequest(SurveyTestFixtures.answerList(3, 3, 3, 3, 3, 3), InterestCategory.FOOD));

        SurveyResponse res = service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(null, InterestCategory.TRAVEL));

        assertThat(res.interestCategory()).isEqualTo(InterestCategory.TRAVEL);
        assertThat(res.extroversionScore()).isEqualTo(18);
        assertThat(res.status()).isEqualTo(ParticipantStatus.SURVEY_DONE);
        verify(eventPublisher).publish(any(RoomEvent.class)); // 첫 완료 때 1회만
    }

    @Test
    void emptyRequest_is400() {
        assertThatThrownBy(() -> service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(null, null)))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void duplicateQuestionNumber_is400() {
        List<PersonalityAnswer> dup = List.of(
                new PersonalityAnswer(1, 3), new PersonalityAnswer(1, 4), new PersonalityAnswer(3, 3),
                new PersonalityAnswer(4, 3), new PersonalityAnswer(5, 3), new PersonalityAnswer(6, 3));

        assertThatThrownBy(() -> service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(dup, null)))
                .isInstanceOfSatisfying(IcelinkException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.properties()).containsKey("errors");
                });
        assertThat(me.isPersonalityDone()).isFalse();
    }

    @Test
    void rejectedWhenRoomNotWaiting() {
        room.startTeamBuilding(NOW);

        assertThatThrownBy(() -> service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(null, InterestCategory.GAME)))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void rejectedWhenParticipantAlreadyAssigned() {
        me.submitPersonality(SurveyTestFixtures.uniform(3));
        me.selectCategory(InterestCategory.GAME);
        me.assign();

        assertThatThrownBy(() -> service.submit("K7M3PQ", GUEST, new SurveySubmitRequest(null, InterestCategory.FOOD)))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void forbiddenForNonParticipant() {
        User stranger = User.register("c".repeat(64), "지현", NOW);
        when(participantRepository.findByRoom_IdAndUser_UserKey(12L, stranger.getUserKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit("K7M3PQ", stranger, new SurveySubmitRequest(null, InterestCategory.GAME)))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void get_returnsCurrentState() {
        me.selectCategory(InterestCategory.SPORTS);

        SurveyResponse res = service.get("K7M3PQ", GUEST);

        assertThat(res.categoryDone()).isTrue();
        assertThat(res.personalityDone()).isFalse();
        assertThat(res.extroversionScore()).isNull();
        assertThat(res.extroversionLevel()).isNull();
        assertThat(res.personality()).isEmpty();
    }
}
