package com.kosscchthon.Icelink.team.session.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamStatus;
import com.kosscchthon.Icelink.team.dto.TeamMemberView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 팀 세션 API 의 작은 요청/응답 레코드 모음. */
public final class SessionDtos {

    private SessionDtos() {
    }

    /** POST /teams/{id}/start */
    public record StartTeamResponse(TeamStatus status, boolean started, TeamQuestionResponse currentQuestion) {
    }

    /** PUT /teams/{id}/name */
    public record RenameTeamRequest(
            @Schema(description = "1~20자. 빈 값/null 이면 기본값 \"{teamNo}팀\" 복원", nullable = true)
            @Size(max = Team.NAME_MAX_LENGTH, message = "팀명은 20자 이하여야 합니다")
            String name
    ) {
    }

    public record RenameTeamResponse(Long teamId, int teamNo, String name, boolean isDefaultName, Updater updatedBy) {
        public record Updater(Long participantId, String nickname) {
        }
    }

    /** POST /teams/{id}/questions/next */
    public record NextQuestionRequest(@Schema(description = "SKIP 이면 건너뛰기 (기록용)", nullable = true) String reason) {
    }

    public record NextQuestionResponse(TeamStatus status, boolean generating, Long skippedQuestionId, int questionCount) {
    }

    /** POST /teams/{id}/questions/{qid}/answer */
    public record SubmitAnswerRequest(
            @Schema(description = "프론트 STT 결과. 팀원 한 명의 기기로 녹음한 화자 구분 없는 텍스트", example = "저는 롤을 다시 시작했어요 아 저는 젤다 하다가 밤새웠어요 저는 게임보다 축구 보는 게 좋아요")
            @NotBlank @Size(max = 3000, message = "답변은 3000자 이하여야 합니다")
            String answerText,

            @Schema(description = "녹음 길이(초), 통계용", nullable = true)
            @Min(0) @Max(600)
            Integer speechDurationSec
    ) {
    }

    public record SubmitAnswerResponse(Long questionId, String status, int estimatedSeconds,
                                       @Schema(description = "질문 상한 도달 시 false") boolean nextQuestionGenerated) {
    }

    /** GET /teams/{id}/summary */
    public record TeamSummaryResponse(
            Long teamId,
            int teamNo,
            String name,
            List<TeamMemberView> members,
            InterestCategory category,
            List<String> finalQuestions,
            int questionCount,
            int answeredCount,
            @Schema(nullable = true) Long durationSec,
            List<String> keywords,
            List<Highlight> highlights,
            Instant startedAt,
            Instant finishedAt
    ) {
        public record Highlight(String question, List<String> keywords) {
        }
    }
}
