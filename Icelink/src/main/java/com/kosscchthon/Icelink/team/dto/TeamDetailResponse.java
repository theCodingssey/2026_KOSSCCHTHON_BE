package com.kosscchthon.Icelink.team.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * GET /teams/{teamId}, GET /rooms/{code}/me/team 응답.
 * currentQuestion / keywords 는 3.5 팀 세션에서 채워진다.
 */
public record TeamDetailResponse(
        Long teamId,
        int teamNo,
        String name,
        boolean isDefaultName,
        TeamStatus status,
        InterestCategory category,
        boolean mixed,
        int questionCount,
        int questionLimit,
        List<TeamMemberView> members,
        @Schema(nullable = true, description = "3.5 에서 채움") Object currentQuestion,
        List<String> keywords,
        Instant startedAt,
        Instant finishedAt
) {

    public static TeamDetailResponse from(Team team, List<TeamMemberView> members) {
        return new TeamDetailResponse(
                team.getId(),
                team.getTeamNo(),
                team.getName(),
                team.isDefaultName(),
                team.getStatus(),
                team.getCategory(),
                team.isMixed(),
                team.getQuestionCount(),
                Team.QUESTION_LIMIT,
                members,
                null,
                List.of(),
                team.getStartedAt(),
                team.getFinishedAt());
    }
}
