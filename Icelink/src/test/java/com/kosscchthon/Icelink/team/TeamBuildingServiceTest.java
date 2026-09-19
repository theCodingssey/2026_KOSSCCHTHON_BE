package com.kosscchthon.Icelink.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomRepository;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.survey.SurveyTestFixtures;
import com.kosscchthon.Icelink.team.dto.TeamBuildingRequest;
import com.kosscchthon.Icelink.team.dto.TeamBuildingResponse;
import com.kosscchthon.Icelink.user.User;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamBuildingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);

    @Mock RoomRepository roomRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock TeamRepository teamRepository;
    @Mock RoomEventPublisher eventPublisher;

    private TeamBuildingService service;
    private Room room;
    private final AtomicLong nextId = new AtomicLong(100);

    @BeforeEach
    void setUp() throws Exception {
        service = new TeamBuildingService(new RoomAccessChecker(roomRepository), participantRepository, teamRepository,
                eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        room = Room.create("K7M3PQ", HOST, "t", "s", 4, List.of(), NOW, Duration.ofHours(24));
        setId(room, Room.class, 12L);
        lenient().when(roomRepository.findByCode("K7M3PQ")).thenReturn(Optional.of(room));
        lenient().when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            setId(t, Team.class, nextId.incrementAndGet());
            return t;
        });
    }

    private static void setId(Object entity, Class<?> type, long id) throws Exception {
        Field f = type.getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private Participant participant(long id, String nick, Integer score, InterestCategory category) throws Exception {
        Participant p = Participant.join(room, User.register(Long.toString(id).repeat(64).substring(0, 64), nick, NOW), nick, NOW);
        setId(p, Participant.class, id);
        if (score != null) {
            int base = score / 6;
            int rem = score - base * 6;
            int[] s = new int[6];
            for (int i = 0; i < 6; i++) {
                s[i] = base + (i < rem ? 1 : 0);
            }
            p.submitPersonality(SurveyTestFixtures.answers(s[0], s[1], s[2], s[3], s[4], s[5]));
        }
        if (category != null) {
            p.selectCategory(category);
        }
        return p;
    }

    @Test
    void buildsTeamsAssignsParticipantsMarksLateAndPublishesEvents() throws Exception {
        List<Participant> active = new ArrayList<>();
        int[] scores = {28, 26, 22, 20, 17, 15, 12, 9};
        for (int i = 0; i < 8; i++) {
            active.add(participant(1 + i, "g" + i, scores[i], InterestCategory.GAME));
        }
        active.add(participant(50, "late", null, null)); // 설문 미완료 → LATE
        when(participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(12L, ParticipantStatus.LEFT)).thenReturn(active);

        TeamBuildingResponse res = service.build("K7M3PQ", HOST, TeamBuildingRequest.defaults());

        assertThat(res.roomStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(res.teamCount()).isEqualTo(2);
        assertThat(res.assignedCount()).isEqualTo(8);
        assertThat(res.lateCount()).isEqualTo(1);
        assertThat(res.teams().get(0).teamNo()).isEqualTo(1);
        assertThat(res.teams().get(0).name()).isEqualTo("1팀");
        assertThat(res.teams().get(1).name()).isEqualTo("2팀");
        assertThat(res.teams().get(0).members()).hasSize(4);
        assertThat(res.teams().get(0).members().get(0).extroversionScore()).isNotNull();
        assertThat(res.categoryGroups()).hasSize(1);
        assertThat(res.categoryGroups().get(0).participantCount()).isEqualTo(8);

        assertThat(active.get(0).getStatus()).isEqualTo(ParticipantStatus.ASSIGNED);
        assertThat(active.get(0).getTeam()).isNotNull();
        assertThat(active.get(8).getStatus()).isEqualTo(ParticipantStatus.LATE);
        assertThat(active.get(8).getTeam()).isNull();
        verify(teamRepository, times(2)).save(any(Team.class));

        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(eventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0).type()).isEqualTo(RoomEventType.TEAM_BUILDING_STARTED);
        RoomEvent completed = captor.getAllValues().get(1);
        assertThat(completed.type()).isEqualTo(RoomEventType.TEAM_BUILDING_COMPLETED);
        assertThat(completed.payload()).containsEntry("teamCount", 2).containsEntry("lateCount", 1);
        assertThat((List<?>) completed.payload().get("assignments")).hasSize(8);
    }

    @Test
    void includeIncomplete_assignsSurveyLessParticipantsWithNeutralScore() throws Exception {
        List<Participant> active = new ArrayList<>();
        active.add(participant(1, "a", 30, InterestCategory.GAME));
        active.add(participant(2, "b", 6, InterestCategory.GAME));
        active.add(participant(3, "c", null, InterestCategory.GAME)); // 점수 없음 → 18
        active.add(participant(4, "d", null, null));                  // 카테고리 없음 → 잔여
        when(participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(12L, ParticipantStatus.LEFT)).thenReturn(active);

        TeamBuildingResponse res = service.build("K7M3PQ", HOST, new TeamBuildingRequest(true));

        assertThat(res.assignedCount()).isEqualTo(4);
        assertThat(res.lateCount()).isZero();
        assertThat(active).allMatch(p -> p.getStatus() == ParticipantStatus.ASSIGNED);
        assertThat(res.teams()).hasSize(1);
        assertThat(res.teams().get(0).mixed()).isTrue();
    }

    @Test
    void notEnoughSurveyDoneParticipants_is409_andRoomStaysWaiting() throws Exception {
        List<Participant> active = List.of(
                participant(1, "a", 20, InterestCategory.GAME),
                participant(2, "b", null, null));
        when(participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(12L, ParticipantStatus.LEFT)).thenReturn(active);

        assertThatThrownBy(() -> service.build("K7M3PQ", HOST, TeamBuildingRequest.defaults()))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_ENOUGH_PARTICIPANTS));
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        verify(teamRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void rejectedWhenRoomNotWaiting() {
        room.startTeamBuilding(NOW);
        room.completeTeamBuilding(NOW);

        assertThatThrownBy(() -> service.build("K7M3PQ", HOST, TeamBuildingRequest.defaults()))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void forbiddenForNonHost() {
        User other = User.register("b".repeat(64), "지현", NOW);

        assertThatThrownBy(() -> service.build("K7M3PQ", other, TeamBuildingRequest.defaults()))
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
