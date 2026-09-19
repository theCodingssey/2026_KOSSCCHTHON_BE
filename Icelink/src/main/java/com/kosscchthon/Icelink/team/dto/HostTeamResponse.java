package com.kosscchthon.Icelink.team.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** GET /host/rooms/{code}/teams 원소. 주최자 모니터링용. */
public record HostTeamResponse(
        Long teamId,
        int teamNo,
        String name,
        TeamStatus status,
        InterestCategory category,
        boolean mixed,
        Double extroversionAvg,
        int questionCount,
        @Schema(nullable = true, description = "3.5 에서 채움") Object currentQuestion,
        List<TeamMemberView> members,
        Instant startedAt,
        Instant finishedAt
) {

    public static HostTeamResponse from(Team team, List<TeamMemberView> members) {
        return new HostTeamResponse(
                team.getId(),
                team.getTeamNo(),
                team.getName(),
                team.getStatus(),
                team.getCategory(),
                team.isMixed(),
                team.getExtroversionAvg(),
                team.getQuestionCount(),
                null,
                members,
                team.getStartedAt(),
                team.getFinishedAt());
    }
}
