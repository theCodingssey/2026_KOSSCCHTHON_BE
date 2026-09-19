package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantAccessChecker;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.team.dto.HostTeamResponse;
import com.kosscchthon.Icelink.team.dto.TeamDetailResponse;
import com.kosscchthon.Icelink.team.dto.TeamMemberView;
import com.kosscchthon.Icelink.user.User;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 조회. 세션 진행(질문·답변)은 3.5 TeamSessionService. */
@Service
@RequiredArgsConstructor
public class TeamQueryService {

    private final TeamAccessChecker teamAccessChecker;
    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantAccessChecker participantAccessChecker;
    private final TeamRepository teamRepository;
    private final ParticipantRepository participantRepository;

    /** GET /teams/{teamId} — 팀원|호스트 */
    @Transactional(readOnly = true)
    public TeamDetailResponse getTeam(Long teamId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        return TeamDetailResponse.from(team, membersFor(team, user.getUserKey()));
    }

    /** GET /rooms/{code}/me/team — 참가자. 미배정이면 404 TEAM_NOT_FOUND */
    @Transactional(readOnly = true)
    public TeamDetailResponse getMyTeam(String code, User user) {
        Participant me = participantAccessChecker.requireParticipant(code, user.getUserKey());
        Team team = me.getTeam();
        if (team == null) {
            throw new IcelinkException(ErrorCode.TEAM_NOT_FOUND, "아직 팀이 배정되지 않았습니다.");
        }
        return TeamDetailResponse.from(team, membersFor(team, user.getUserKey()));
    }

    /** GET /host/rooms/{code}/teams — 호스트 */
    @Transactional(readOnly = true)
    public List<HostTeamResponse> listForHost(String code, User host) {
        Room room = roomAccessChecker.requireHost(code, host.getUserKey());
        List<Team> teams = teamRepository.findAllByRoom_IdOrderByTeamNoAsc(room.getId());
        if (teams.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Participant>> membersByTeam = participantRepository
                .findAllByRoom_IdAndTeamIsNotNullOrderByIdAsc(room.getId()).stream()
                .collect(Collectors.groupingBy(p -> p.getTeam().getId()));
        return teams.stream()
                .map(t -> HostTeamResponse.from(t, membersByTeam.getOrDefault(t.getId(), List.of()).stream()
                        .map(TeamMemberView::forHost)
                        .toList()))
                .toList();
    }

    private List<TeamMemberView> membersFor(Team team, String viewerUserKey) {
        return participantRepository.findAllByTeam_IdOrderByIdAsc(team.getId()).stream()
                .map(p -> TeamMemberView.forParticipant(p, viewerUserKey))
                .toList();
    }
}
