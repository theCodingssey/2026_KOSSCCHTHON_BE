package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.team.dto.TeamBuildingRequest;
import com.kosscchthon.Icelink.team.dto.TeamBuildingResponse;
import com.kosscchthon.Icelink.team.dto.TeamMemberView;
import com.kosscchthon.Icelink.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /host/rooms/{code}/team-building (T-01~T-08).
 * 방 WAITING → TEAM_BUILDING → IN_PROGRESS. 실패 시 트랜잭션 롤백으로 WAITING 유지.
 */
@Service
@RequiredArgsConstructor
public class TeamBuildingService {

    /** 성격 미응답자를 포함할 때 쓰는 중앙값 */
    static final int NEUTRAL_EXTROVERSION = 18;
    static final int MIN_PARTICIPANTS = 2;

    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantRepository participantRepository;
    private final TeamRepository teamRepository;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public TeamBuildingResponse build(String code, User host, TeamBuildingRequest request) {
        Room room = roomAccessChecker.requireHost(code, host.getUserKey());
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "팀 빌딩은 대기 중(WAITING)인 방에서만 실행할 수 있습니다. (현재 상태: " + room.getStatus() + ")");
        }

        List<Participant> active = participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(
                room.getId(), ParticipantStatus.LEFT);
        boolean includeIncomplete = request != null && request.includeIncomplete();
        List<Participant> eligible = active.stream()
                .filter(p -> includeIncomplete || p.getStatus() == ParticipantStatus.SURVEY_DONE)
                .toList();
        if (eligible.size() < MIN_PARTICIPANTS) {
            throw new IcelinkException(ErrorCode.NOT_ENOUGH_PARTICIPANTS,
                    "팀 빌딩에는 설문을 완료한 참가자가 " + MIN_PARTICIPANTS + "명 이상 필요합니다. (현재 " + eligible.size() + "명)");
        }

        Instant now = Instant.now(clock);
        room.startTeamBuilding(now);
        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.TEAM_BUILDING_STARTED, Map.of(), now));

        List<TeamBuilder.Member> members = eligible.stream()
                .map(p -> new TeamBuilder.Member(
                        p.getId(),
                        p.getExtroversionScore() == null ? NEUTRAL_EXTROVERSION : p.getExtroversionScore(),
                        p.getInterestCategory()))
                .toList();
        TeamBuilder.Result result = TeamBuilder.build(members, room.getTeamSize());

        Map<Long, Participant> byId = eligible.stream().collect(Collectors.toMap(Participant::getId, Function.identity()));
        List<TeamBuildingResponse.BuiltTeamView> teamViews = new ArrayList<>();
        Map<Long, Integer> teamNoByParticipant = new HashMap<>();
        int teamNo = 1;
        for (TeamBuilder.BuiltTeam built : result.teams()) {
            Team team = teamRepository.save(Team.create(room, teamNo, built.category(), built.mixed(), built.extroversionAvg()));
            List<TeamMemberView> memberViews = new ArrayList<>();
            for (TeamBuilder.Member m : built.members()) {
                Participant p = byId.get(m.participantId());
                p.assignTo(team);
                teamNoByParticipant.put(p.getId(), teamNo);
                memberViews.add(TeamMemberView.forHost(p));
            }
            teamViews.add(new TeamBuildingResponse.BuiltTeamView(team.getId(), teamNo, team.getName(),
                    team.getCategory(), team.isMixed(), team.getExtroversionAvg(), memberViews));
            teamNo++;
        }

        int late = 0;
        for (Participant p : active) {
            if (!byId.containsKey(p.getId())) {
                p.markLate();
                late++;
            }
        }

        room.completeTeamBuilding(now);

        List<Map<String, Object>> assignments = eligible.stream()
                .map(p -> Map.<String, Object>of("participantId", p.getId(), "teamNo", teamNoByParticipant.get(p.getId())))
                .toList();
        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.TEAM_BUILDING_COMPLETED, Map.of(
                "teamCount", result.teams().size(),
                "assignedCount", eligible.size(),
                "lateCount", late,
                "assignments", assignments
        ), now));

        List<TeamBuildingResponse.CategoryGroupView> groupViews = result.groups().stream()
                .map(g -> new TeamBuildingResponse.CategoryGroupView(g.category(), g.participantCount(), g.teamCount(), g.leftover()))
                .toList();
        return new TeamBuildingResponse(room.getStatus(), result.teams().size(), eligible.size(), late, groupViews, teamViews);
    }
}
