package com.kosscchthon.Icelink.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.dto.CreateRoomRequest;
import com.kosscchthon.Icelink.room.dto.FinishRoomResponse;
import com.kosscchthon.Icelink.room.dto.ParticipantCounts;
import com.kosscchthon.Icelink.room.dto.RoomDetailResponse;
import com.kosscchthon.Icelink.room.dto.RoomPublicResponse;
import com.kosscchthon.Icelink.room.dto.RoomResponse;
import com.kosscchthon.Icelink.room.dto.UpdateFinalQuestionsRequest;
import com.kosscchthon.Icelink.room.dto.UpdateRoomRequest;
import com.kosscchthon.Icelink.room.port.RoomParticipantQuery;
import com.kosscchthon.Icelink.room.port.RoomTeamQuery;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String HOST_KEY = "a".repeat(64);
    private static final String OTHER_KEY = "b".repeat(64);
    private static final User HOST = User.register(HOST_KEY, "민수", NOW);
    private static final User OTHER = User.register(OTHER_KEY, "지현", NOW);
    private static final RoomProperties PROPS = new RoomProperties("https://icelink.app/join", "icelink://join?code=", Duration.ofHours(24));

    @Mock RoomRepository roomRepository;
    @Mock UserRepository userRepository;
    @Mock RoomCodeGenerator codeGenerator;
    @Mock RoomParticipantQuery participantQuery;
    @Mock RoomTeamQuery teamQuery;
    @Mock RoomEventPublisher eventPublisher;

    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomRepository, userRepository, codeGenerator, new RoomAccessChecker(roomRepository),
                participantQuery, teamQuery, eventPublisher, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Room existingRoom() {
        return Room.create("K7M3PQ", HOST, "제목", "상황", 4, List.of("q1"), NOW.minusSeconds(60), Duration.ofHours(24));
    }

    @Nested
    class Create {

        @Test
        void createsWaitingRoomWithCodeLinksAndTrimmedFields() {
            when(roomRepository.existsByHost_UserKeyAndStatusIn(HOST_KEY, RoomStatus.ACTIVE)).thenReturn(false);
            when(codeGenerator.generateUnique()).thenReturn("K7M3PQ");
            when(userRepository.getReferenceById(HOST_KEY)).thenReturn(HOST);

            RoomResponse res = service.create(HOST, new CreateRoomRequest(" 제목 ", " 상황 ", 4, List.of(" q1 ", "  ")));

            assertThat(res.code()).isEqualTo("K7M3PQ");
            assertThat(res.status()).isEqualTo(RoomStatus.WAITING);
            assertThat(res.title()).isEqualTo("제목");
            assertThat(res.situation()).isEqualTo("상황");
            assertThat(res.finalQuestions()).containsExactly("q1");
            assertThat(res.inviteUrl()).isEqualTo("https://icelink.app/join/K7M3PQ");
            assertThat(res.deepLink()).isEqualTo("icelink://join?code=K7M3PQ");
            assertThat(res.host().userKey()).isEqualTo(HOST_KEY);
            assertThat(res.host().name()).isEqualTo("민수");
            assertThat(res.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
            verify(roomRepository).save(any(Room.class));
        }

        @Test
        void nullFinalQuestionsBecomesEmptyList() {
            when(codeGenerator.generateUnique()).thenReturn("K7M3PQ");
            when(userRepository.getReferenceById(HOST_KEY)).thenReturn(HOST);

            RoomResponse res = service.create(HOST, new CreateRoomRequest("t", "s", 4, null));

            assertThat(res.finalQuestions()).isEmpty();
        }

        @Test
        void rejectsWhenHostAlreadyHasActiveRoom() {
            when(roomRepository.existsByHost_UserKeyAndStatusIn(HOST_KEY, RoomStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> service.create(HOST, new CreateRoomRequest("t", "s", 4, List.of())))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_IN_ANOTHER_ROOM));
            verify(roomRepository, never()).save(any());
        }
    }

    @Nested
    class Read {

        @Test
        void getPublic_normalizesCodeAndComputesJoinable() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
            when(participantQuery.countActive(room.getId())).thenReturn(17);

            RoomPublicResponse res = service.getPublic(" k7m3pq ");

            assertThat(res.code()).isEqualTo("K7M3PQ");
            assertThat(res.participantCount()).isEqualTo(17);
            assertThat(res.joinable()).isTrue();
        }

        @Test
        void getPublic_notJoinableWhenFullOrNotWaiting() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
            when(participantQuery.countActive(room.getId())).thenReturn(Room.PARTICIPANT_LIMIT);

            assertThat(service.getPublic("K7M3PQ").joinable()).isFalse();
        }

        @Test
        void getPublic_unknownCodeIs404() {
            when(roomRepository.findByCode("XXXXXX")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPublic("XXXXXX"))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        }

        @Test
        void hostDetail_aggregatesCountsParticipantsTeams() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
            when(participantQuery.counts(room.getId())).thenReturn(new ParticipantCounts(3, 2, 0, 0, 5));
            when(participantQuery.listActive(room.getId())).thenReturn(List.of());
            when(teamQuery.listByRoom(room.getId())).thenReturn(List.of());

            RoomDetailResponse res = service.getHostDetail("K7M3PQ", HOST);

            assertThat(res.counts().total()).isEqualTo(5);
            assertThat(res.inviteUrl()).endsWith("/K7M3PQ");
            assertThat(res.finalQuestions()).containsExactly("q1");
        }

        @Test
        void hostDetail_forbiddenForNonHost() {
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(existingRoom()));

            assertThatThrownBy(() -> service.getHostDetail("K7M3PQ", OTHER))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @Nested
    class Update {

        @Test
        void updateSettings_appliesOnlyGivenFieldsAndPublishesRoomUpdated() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));

            RoomResponse res = service.updateSettings("K7M3PQ", HOST, new UpdateRoomRequest(null, null, 6));

            assertThat(res.teamSize()).isEqualTo(6);
            assertThat(res.title()).isEqualTo("제목");
            ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(RoomEventType.ROOM_UPDATED);
            assertThat(captor.getValue().payload()).containsEntry("teamSize", 6);
        }

        @Test
        void updateSettings_emptyBodyIsNoopWithoutEvent() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));

            service.updateSettings("K7M3PQ", HOST, new UpdateRoomRequest(null, null, null));

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        void updateSettings_rejectedWhenNotWaiting() {
            Room room = existingRoom();
            room.startTeamBuilding(NOW);
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> service.updateSettings("K7M3PQ", HOST, new UpdateRoomRequest("x", null, null)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void updateFinalQuestions_worksWhileInProgress_andTrims() {
            Room room = existingRoom();
            room.startTeamBuilding(NOW);
            room.completeTeamBuilding(NOW);
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));

            var res = service.updateFinalQuestions("K7M3PQ", HOST, new UpdateFinalQuestionsRequest(List.of(" a ", "b")));

            assertThat(res.finalQuestions()).containsExactly("a", "b");
            assertThat(res.updatedAt()).isEqualTo(NOW);
            verify(eventPublisher).publish(any(RoomEvent.class));
        }

        @Test
        void updateFinalQuestions_rejectsMoreThanFiveAfterTrim() {
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(existingRoom()));

            assertThatThrownBy(() -> service.updateFinalQuestions("K7M3PQ", HOST,
                    new UpdateFinalQuestionsRequest(List.of("1", "2", "3", "4", "5", "6"))))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @Nested
    class Finish {

        @Test
        void finish_transitionsRoomFinishesTeamsAndPublishesFinalQuestions() {
            Room room = existingRoom();
            room.startTeamBuilding(NOW);
            room.completeTeamBuilding(NOW);
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
            when(teamQuery.finishAll(eq(room.getId()), eq(NOW))).thenReturn(5);

            FinishRoomResponse res = service.finish("K7M3PQ", HOST);

            assertThat(res.roomStatus()).isEqualTo(RoomStatus.FINISHED);
            assertThat(res.finishedAt()).isEqualTo(NOW);
            assertThat(res.finishedTeamCount()).isEqualTo(5);
            assertThat(res.finalQuestions()).containsExactly("q1");

            ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(RoomEventType.ROOM_FINISHED);
            assertThat(captor.getValue().payload()).containsEntry("finalQuestions", List.of("q1"));
        }

        @Test
        void finish_isIdempotent_secondCallDoesNotRepublish() {
            Room room = existingRoom();
            room.finish(NOW.minusSeconds(30));
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));

            FinishRoomResponse res = service.finish("K7M3PQ", HOST);

            assertThat(res.roomStatus()).isEqualTo(RoomStatus.FINISHED);
            assertThat(res.finishedAt()).isEqualTo(NOW.minusSeconds(30));
            assertThat(res.finishedTeamCount()).isZero();
            verify(teamQuery, never()).finishAll(anyLong(), any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        void finish_allowedFromWaiting() {
            Room room = existingRoom();
            when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
            when(teamQuery.finishAll(any(), any())).thenReturn(0);

            assertThat(service.finish("K7M3PQ", HOST).roomStatus()).isEqualTo(RoomStatus.FINISHED);
        }
    }
}
