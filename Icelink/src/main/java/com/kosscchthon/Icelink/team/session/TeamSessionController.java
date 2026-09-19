package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.StartTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.TeamSummaryResponse;
import com.kosscchthon.Icelink.team.session.dto.TeamQuestionResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 팀 세션 진행 API. 조회는 팀원|호스트, 조작은 팀원만. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/teams/{teamId}")
@RequiredArgsConstructor
@Tag(name = "Team", description = "팀 조회·세션 진행")
public class TeamSessionController {

    private final TeamSessionService sessionService;

    @PostMapping("/start")
    @Operation(summary = "세션 시작 (모두 모였어요)", description = "NOT_STARTED → NAMING, 자기소개·팀명 질문(INTRO) 생성. 이미 시작됐으면 현재 상태 반환(멱등).")
    public StartTeamResponse start(@PathVariable Long teamId, @CurrentUser User user) {
        return sessionService.start(teamId, user);
    }

    @PutMapping("/name")
    @Operation(summary = "팀명 수정", description = "기본값 \"N팀\"을 덮어쓴다. 빈 값이면 기본값 복원. 방 내 중복 불가.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "409", description = "TEAM_NAME_DUPLICATED / INVALID_STATE_TRANSITION(종료됨)")
    })
    public RenameTeamResponse rename(@PathVariable Long teamId, @CurrentUser User user,
                                     @Valid @RequestBody(required = false) RenameTeamRequest request) {
        return sessionService.rename(teamId, user, request);
    }

    @GetMapping("/questions")
    @Operation(summary = "질문 이력", description = "순서대로. 답변이 있으면 answer 포함.")
    public List<TeamQuestionResponse> questions(@PathVariable Long teamId, @CurrentUser User user) {
        return sessionService.listQuestions(teamId, user);
    }

    @GetMapping("/questions/current")
    @Operation(summary = "현재 질문", description = "세션 미시작이면 404 QUESTION_NOT_FOUND.")
    public TeamQuestionResponse current(@PathVariable Long teamId, @CurrentUser User user) {
        return sessionService.getCurrent(teamId, user);
    }

    @GetMapping("/questions/{questionId}")
    @Operation(summary = "질문 단건 (폴링용)", description = "DONE 이면 answer.keywords, FAILED 면 failureReason·retryable.")
    public TeamQuestionResponse question(@PathVariable Long teamId, @PathVariable Long questionId, @CurrentUser User user) {
        return sessionService.getQuestion(teamId, questionId, user);
    }

    @PostMapping("/questions/next")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "다음 질문 요청",
            description = "NAMING 에서 자기소개를 마치고 첫 AI 질문으로, 또는 QUESTIONING 에서 현재 질문 건너뛰기. "
                    + "202 후 비동기 생성 → SSE QUESTION_CREATED 또는 GET /questions/current 폴링.")
    @ApiResponses({
            @ApiResponse(responseCode = "202"),
            @ApiResponse(responseCode = "409", description = "QUESTION_LIMIT_EXCEEDED / INVALID_STATE_TRANSITION(처리 중·생성 중)")
    })
    public NextQuestionResponse next(@PathVariable Long teamId, @CurrentUser User user,
                                     @RequestBody(required = false) NextQuestionRequest request) {
        return sessionService.next(teamId, user, request);
    }

    @PostMapping("/questions/{questionId}/answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "대화 텍스트 제출",
            description = "프론트 STT 결과를 보낸다. 질문 PROCESSING → 비동기로 키워드 추출·꼬리질문 생성 → DONE + 새 질문.")
    @ApiResponses({
            @ApiResponse(responseCode = "202"),
            @ApiResponse(responseCode = "409", description = "ANSWER_ALREADY_SUBMITTED / INVALID_STATE_TRANSITION(현재 질문 아님)")
    })
    public SubmitAnswerResponse answer(@PathVariable Long teamId, @PathVariable Long questionId, @CurrentUser User user,
                                       @Valid @RequestBody SubmitAnswerRequest request) {
        return sessionService.submitAnswer(teamId, questionId, user, request);
    }

    @PostMapping("/questions/{questionId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "실패 재처리", description = "FAILED → PROCESSING. 재시도도 실패하면 기본 질문으로 흐름을 이어간다.")
    public SubmitAnswerResponse retry(@PathVariable Long teamId, @PathVariable Long questionId, @CurrentUser User user) {
        return sessionService.retry(teamId, questionId, user);
    }

    @GetMapping("/summary")
    @Operation(summary = "종료 요약", description = "방 FINISHED 후. 마무리 질문, 키워드, 질문별 하이라이트.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — 방이 아직 종료되지 않음")
    })
    public TeamSummaryResponse summary(@PathVariable Long teamId, @CurrentUser User user) {
        return sessionService.summary(teamId, user);
    }
}
