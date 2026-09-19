package com.kosscchthon.Icelink.team.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamStatus;
import com.kosscchthon.Icelink.team.session.dto.TeamQuestionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * GET /teams/{teamId}, GET /rooms/{code}/me/team 응답.
 * currentQuestion 은 가장 최근 질문, keywords 는 팀 답변에서 추출된 누적 키워드.
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
        @Schema(nullable = true, description = "세션 시작 후 가장 최근 질문") TeamQuestionResponse currentQuestion,
        List<String> keywords,
        Instant startedAt,
        Instant finishedAt
) {

    public static TeamDetailResponse from(Team team, List<TeamMemberView> members,
                                          TeamQuestionResponse currentQuestion, List<String> keywords) {
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
                currentQuestion,
                keywords,
                team.getStartedAt(),
                team.getFinishedAt());
    }
}
