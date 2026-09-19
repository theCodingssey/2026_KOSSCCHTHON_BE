package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.room.dto.RoomTeamSummary;
import com.kosscchthon.Icelink.room.port.RoomTeamQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** room 모듈의 {@link RoomTeamQuery} 포트 구현. */
@Component
@RequiredArgsConstructor
public class JpaRoomTeamQuery implements RoomTeamQuery {

    private final TeamRepository teamRepository;
    private final ParticipantRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoomTeamSummary> listByRoom(Long roomId) {
        List<Team> teams = teamRepository.findAllByRoom_IdOrderByTeamNoAsc(roomId);
        if (teams.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> memberCounts = participantRepository.countMembersByTeam(roomId);
        return teams.stream()
                .map(t -> new RoomTeamSummary(t.getId(), t.getTeamNo(), t.getName(), t.getStatus().name(),
                        memberCounts.getOrDefault(t.getId(), 0L).intValue(), t.getCategory().name(), t.isMixed(),
                        t.getExtroversionAvg(), t.getQuestionCount()))
                .toList();
    }

    @Override
    @Transactional
    public int finishAll(Long roomId, Instant now) {
        int finished = 0;
        for (Team team : teamRepository.findAllByRoom_IdOrderByTeamNoAsc(roomId)) {
            if (team.finish(now)) {
                finished++;
            }
        }
        return finished;
    }
}
