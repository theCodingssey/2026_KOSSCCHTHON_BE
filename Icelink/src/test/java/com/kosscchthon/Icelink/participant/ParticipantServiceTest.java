package com.kosscchthon.Icelink.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.ParticipantService.JoinResult;
import com.kosscchthon.Icelink.participant.dto.HostParticipantResponse;
import com.kosscchthon.Icelink.participant.dto.JoinRoomRequest;
import com.kosscchthon.Icelink.participant.dto.ParticipantMeResponse;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.survey.SurveyTestFixtures;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomRepository;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserRepository;
import java.lang.reflect.Field;
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
class ParticipantServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);
    private static final User GUEST = User.register("b".repeat(64), "민수", NOW);
    private static final User OTHER = User.register("c".repeat(64), "지현", NOW);

    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock RoomRepository roomRepository;
    @Mock RoomEventPublisher eventPublisher;

    private ParticipantService service;
    private Room room;

    @BeforeEach
    void setUp() throws Exception {
        RoomAccessChecker roomAccess = new RoomAccessChecker(roomRepository);
        service = new ParticipantService(participantRepository, userRepository, roomAccess,
                new ParticipantAccessChecker(roomAccess, participantRepository), eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        room = Room.create("K7M3PQ", HOST, "t", "s", 4, List.of(), NOW.minusSeconds(60), Duration.ofHours(24));
        setId(room, Room.class, 12L);
        lenient().when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
    }

    private static void setId(Object entity, Class<?> type, long id) throws Exception {
        Field f = type.getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    /** DB 저장을 흉내내 ID 를 부여한다. */
    private static Participant withId(Participant p, long id) {
        try {
            setId(p, Participant.class, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }

    private Participant activeParticipant(User user, String nickname, long id) throws Exception {
        Participant p = Participant.join(room, user, nickname, NOW.minusSeconds(30));
        setId(p, Participant.class, id);
        return p;
    }

    @Nested
    class Join {

        @Test
        void createsParticipantWithUserNameAsDefaultNickname_andPublishesJoined() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(GUEST.getUserKey(), RoomStatus.ACTIVE)).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(12L, ParticipantStatus.LEFT)).thenReturn(3);
            when(participantRepository.findActiveNicknamesLower(12L)).thenReturn(List.of("지현"));
            when(userRepository.getReferenceById(GUEST.getUserKey())).thenReturn(GUEST);
            when(participantRepository.saveAndFlush(any(Participant.class))).thenAnswer(inv -> withId(inv.getArgument(0), 101L));

            JoinResult result = service.join("K7M3PQ", GUEST, JoinRoomRequest.empty());

            assertThat(result.created()).isTrue();
            assertThat(result.response().nickname()).isEqualTo("민수");
            assertThat(result.response().status()).isEqualTo(ParticipantStatus.JOINED);
            assertThat(result.response().room().code()).isEqualTo("K7M3PQ");

            ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(RoomEventType.PARTICIPANT_JOINED);
            assertThat(captor.getValue().payload()).containsEntry("participantCount", 4).containsEntry("nickname", "민수");
        }

        @Test
        void usesGivenNicknameTrimmed() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(any(), any())).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(0);
            when(participantRepository.findActiveNicknamesLower(12L)).thenReturn(List.of());
            when(userRepository.getReferenceById(any())).thenReturn(GUEST);
            when(participantRepository.saveAndFlush(any(Participant.class))).thenAnswer(inv -> withId(inv.getArgument(0), 101L));

            JoinResult result = service.join("K7M3PQ", GUEST, new JoinRoomRequest("  감자 "));

            assertThat(result.response().nickname()).isEqualTo("감자");
        }

        @Test
        void idempotent_returnsExistingActiveParticipationWithoutEvent() throws Exception {
            Participant existing = activeParticipant(GUEST, "민수", 101L);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(existing));

            JoinResult result = service.join("K7M3PQ", GUEST, new JoinRoomRequest("다른이름"));

            assertThat(result.created()).isFalse();
            assertThat(result.response().participantId()).isEqualTo(101L);
            assertThat(result.response().nickname()).isEqualTo("민수");
            verify(participantRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        void rejoinAfterLeaving_reactivatesSameRow() throws Exception {
            Participant left = activeParticipant(GUEST, "민수", 101L);
            left.submitPersonality(SurveyTestFixtures.answers(4, 3, 3, 4, 3, 3));
            left.leave(NOW.minusSeconds(10));
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(left));
            when(participantRepository.findActiveParticipation(any(), any())).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(2);
            when(participantRepository.findActiveNicknamesLower(12L)).thenReturn(List.of());

            JoinResult result = service.join("K7M3PQ", GUEST, JoinRoomRequest.empty());

            assertThat(result.created()).isTrue();
            assertThat(result.response().participantId()).isEqualTo(101L);
            assertThat(left.getStatus()).isEqualTo(ParticipantStatus.JOINED);
            assertThat(left.isPersonalityDone()).isFalse();
            verify(participantRepository, never()).saveAndFlush(any());
        }

        @Test
        void hostCannotJoinOwnRoom() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, HOST.getUserKey())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.join("K7M3PQ", HOST, JoinRoomRequest.empty()))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.HOST_CANNOT_JOIN));
        }

        @Test
        void rejectedWhenRoomNotWaiting() {
            room.startTeamBuilding(NOW);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.join("K7M3PQ", GUEST, JoinRoomRequest.empty()))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ROOM_NOT_WAITING));
        }

        @Test
        void rejectedWhenActiveInAnotherRoom() throws Exception {
            Room otherRoom = Room.create("ZZZZZZ", OTHER, "o", "o", 4, List.of(), NOW, Duration.ofHours(24));
            setId(otherRoom, Room.class, 99L);
            Participant elsewhere = Participant.join(otherRoom, GUEST, "민수", NOW);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(GUEST.getUserKey(), RoomStatus.ACTIVE)).thenReturn(Optional.of(elsewhere));

            assertThatThrownBy(() -> service.join("K7M3PQ", GUEST, JoinRoomRequest.empty()))
                    .isInstanceOfSatisfying(IcelinkException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_IN_ANOTHER_ROOM);
                        assertThat(e.properties()).containsEntry("activeRoomCode", "ZZZZZZ");
                    });
        }

        @Test
        void rejectedWhenRoomFull() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(any(), any())).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(12L, ParticipantStatus.LEFT)).thenReturn(Room.PARTICIPANT_LIMIT);

            assertThatThrownBy(() -> service.join("K7M3PQ", GUEST, JoinRoomRequest.empty()))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ROOM_FULL));
        }

        @Test
        void duplicateNickname_isAutoRenamed_caseInsensitive() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(any(), any())).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(1);
            when(participantRepository.findActiveNicknamesLower(12L)).thenReturn(List.of("alex", "alex2"));
            when(userRepository.getReferenceById(any())).thenReturn(GUEST);
            when(participantRepository.saveAndFlush(any(Participant.class))).thenAnswer(inv -> withId(inv.getArgument(0), 101L));

            JoinResult result = service.join("K7M3PQ", GUEST, new JoinRoomRequest("Alex"));

            assertThat(result.created()).isTrue();
            assertThat(result.response().nickname()).isEqualTo("Alex3");
        }

        @Test
        void duplicateNickname_withAllVariantsTaken_is409() {
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.empty());
            when(participantRepository.findActiveParticipation(any(), any())).thenReturn(Optional.empty());
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(1);
            List<String> taken = new java.util.ArrayList<>(List.of("a"));
            for (int n = 2; n <= 99; n++) {
                taken.add("a" + n);
            }
            when(participantRepository.findActiveNicknamesLower(12L)).thenReturn(taken);

            assertThatThrownBy(() -> service.join("K7M3PQ", GUEST, new JoinRoomRequest("a")))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
            verify(participantRepository, never()).saveAndFlush(any());
        }

        @Test
        void unknownRoomIs404() {
            when(roomRepository.findByCode("XXXXXX")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.join("XXXXXX", GUEST, JoinRoomRequest.empty()))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        }
    }

    @Nested
    class Me {

        @Test
        void returnsParticipantRoomAndSurveyView_teamNullBeforeBuilding() throws Exception {
            Participant me = activeParticipant(GUEST, "민수", 101L);
            me.submitPersonality(SurveyTestFixtures.uniform(4));
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(me));
            when(participantRepository.countByRoom_IdAndStatusNot(12L, ParticipantStatus.LEFT)).thenReturn(7);

            ParticipantMeResponse res = service.getMe("K7M3PQ", GUEST);

            assertThat(res.participantId()).isEqualTo(101L);
            assertThat(res.room().participantCount()).isEqualTo(7);
            assertThat(res.room().finalQuestions()).isNull();
            assertThat(res.survey().personalityDone()).isTrue();
            assertThat(res.survey().categoryDone()).isFalse();
            assertThat(res.survey().extroversionScore()).isEqualTo(24);
            assertThat(res.team()).isNull();
        }

        @Test
        void finalQuestionsExposedOnlyAfterFinish() throws Exception {
            room.replaceFinalQuestions(List.of("q1"), NOW);
            room.finish(NOW);
            Participant me = activeParticipant(GUEST, "민수", 101L);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(me));
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(1);

            assertThat(service.getMe("K7M3PQ", GUEST).room().finalQuestions()).containsExactly("q1");
        }

        @Test
        void forbiddenWhenNotParticipantOrLeft() throws Exception {
            Participant left = activeParticipant(GUEST, "민수", 101L);
            left.leave(NOW);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(left));

            assertThatThrownBy(() -> service.getMe("K7M3PQ", GUEST))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @Nested
    class LeaveAndKick {

        @Test
        void leave_marksLeftAndPublishesRemainingCount() throws Exception {
            Participant me = activeParticipant(GUEST, "민수", 101L);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(me));
            when(participantRepository.countByRoom_IdAndStatusNot(12L, ParticipantStatus.LEFT)).thenReturn(5);

            service.leave("K7M3PQ", GUEST);

            assertThat(me.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().type()).isEqualTo(RoomEventType.PARTICIPANT_LEFT);
            assertThat(captor.getValue().payload()).containsEntry("participantCount", 4);
        }

        @Test
        void leave_rejectedAfterTeamBuildingStarted() throws Exception {
            Participant me = activeParticipant(GUEST, "민수", 101L);
            when(participantRepository.findByRoom_IdAndUser_UserKey(12L, GUEST.getUserKey())).thenReturn(Optional.of(me));
            room.startTeamBuilding(NOW);

            assertThatThrownBy(() -> service.leave("K7M3PQ", GUEST))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            assertThat(me.isActive()).isTrue();
        }

        @Test
        void kick_byHost_marksTargetLeft() throws Exception {
            Participant target = activeParticipant(GUEST, "민수", 101L);
            when(participantRepository.findByIdAndRoom_Id(101L, 12L)).thenReturn(Optional.of(target));
            when(participantRepository.countByRoom_IdAndStatusNot(anyLong(), any())).thenReturn(1);

            service.kick("K7M3PQ", HOST, 101L);

            assertThat(target.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            verify(eventPublisher).publish(any(RoomEvent.class));
        }

        @Test
        void kick_byNonHost_isForbidden() {
            assertThatThrownBy(() -> service.kick("K7M3PQ", OTHER, 101L))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        void kick_unknownOrLeftParticipant_is404() throws Exception {
            Participant left = activeParticipant(GUEST, "민수", 101L);
            left.leave(NOW);
            when(participantRepository.findByIdAndRoom_Id(101L, 12L)).thenReturn(Optional.of(left));

            assertThatThrownBy(() -> service.kick("K7M3PQ", HOST, 101L))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.PARTICIPATION_NOT_FOUND));
        }
    }

    @Nested
    class HostList {

        @Test
        void listsActiveParticipantsWithScoresAndCategory() throws Exception {
            Participant a = activeParticipant(GUEST, "민수", 101L);
            a.submitPersonality(SurveyTestFixtures.uniform(4));
            a.selectCategory(InterestCategory.GAME);
            Participant b = activeParticipant(OTHER, "지현", 102L);
            when(participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(12L, ParticipantStatus.LEFT))
                    .thenReturn(List.of(a, b));

            List<HostParticipantResponse> res = service.listForHost("K7M3PQ", HOST);

            assertThat(res).hasSize(2);
            assertThat(res.get(0).status()).isEqualTo(ParticipantStatus.SURVEY_DONE);
            assertThat(res.get(0).extroversionScore()).isEqualTo(24);
            assertThat(res.get(0).interestCategory()).isEqualTo(InterestCategory.GAME);
            assertThat(res.get(1).status()).isEqualTo(ParticipantStatus.JOINED);
            assertThat(res.get(1).extroversionScore()).isNull();
        }

        @Test
        void forbiddenForNonHost() {
            assertThatThrownBy(() -> service.listForHost("K7M3PQ", GUEST))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }
}
