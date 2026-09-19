package com.kosscchthon.Icelink.team.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.team.TeamStatus;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.StartTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerResponse;
import com.kosscchthon.Icelink.team.session.dto.TeamQuestionResponse;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeamSessionController.class)
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class TeamSessionControllerTest {

    private static final String KEY = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "민수", NOW);

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean TeamSessionService sessionService;

    @BeforeEach
    void authenticate() {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
    }

    private static TeamQuestionResponse intro() {
        return new TeamQuestionResponse(8999L, 1, QuestionType.INTRO, "자기소개…", QuestionStatus.ANSWERING, null, null, null, NOW);
    }

    @Test
    void start_returnsStatusAndIntro() throws Exception {
        when(sessionService.start(eq(501L), any(User.class))).thenReturn(new StartTeamResponse(TeamStatus.NAMING, true, intro()));

        mockMvc.perform(post("/api/v1/teams/501/start").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAMING"))
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.currentQuestion.type").value("INTRO"));
    }

    @Test
    void rename_passesBody() throws Exception {
        when(sessionService.rename(eq(501L), any(User.class), any(RenameTeamRequest.class)))
                .thenReturn(new RenameTeamResponse(501L, 1, "감자전사", false, new RenameTeamResponse.Updater(101L, "민수")));

        mockMvc.perform(put("/api/v1/teams/501/name")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"감자전사"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("감자전사"))
                .andExpect(jsonPath("$.isDefaultName").value(false))
                .andExpect(jsonPath("$.updatedBy.nickname").value("민수"));
    }

    @Test
    void rename_tooLong_is400() throws Exception {
        mockMvc.perform(put("/api/v1/teams/501/name")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(sessionService, never()).rename(any(), any(), any());
    }

    @Test
    void rename_duplicate_is409() throws Exception {
        when(sessionService.rename(eq(501L), any(User.class), any()))
                .thenThrow(new IcelinkException(ErrorCode.TEAM_NAME_DUPLICATED, "dup"));

        mockMvc.perform(put("/api/v1/teams/501/name")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"감자전사"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_NAME_DUPLICATED"));
    }

    @Test
    void next_is202() throws Exception {
        when(sessionService.next(eq(501L), any(User.class), any()))
                .thenReturn(new NextQuestionResponse(TeamStatus.QUESTIONING, true, null, 1));

        mockMvc.perform(post("/api/v1/teams/501/questions/next").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.generating").value(true))
                .andExpect(jsonPath("$.status").value("QUESTIONING"));
    }

    @Test
    void next_atLimit_is409() throws Exception {
        when(sessionService.next(eq(501L), any(User.class), any()))
                .thenThrow(new IcelinkException(ErrorCode.QUESTION_LIMIT_EXCEEDED, "limit"));

        mockMvc.perform(post("/api/v1/teams/501/questions/next").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_LIMIT_EXCEEDED"));
    }

    @Test
    void answer_is202WithProcessing() throws Exception {
        when(sessionService.submitAnswer(eq(501L), eq(9001L), any(User.class), any(SubmitAnswerRequest.class)))
                .thenReturn(new SubmitAnswerResponse(9001L, "PROCESSING", 8, true));

        mockMvc.perform(post("/api/v1/teams/501/questions/9001/answer")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerText":"민수: 롤 다시 시작했어요","speechDurationSec":40}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.nextQuestionGenerated").value(true));
    }

    @Test
    void answer_blankText_is400BeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/teams/501/questions/9001/answer")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerText":"   "}
                                """))
                .andExpect(status().isBadRequest());
        verify(sessionService, never()).submitAnswer(any(), any(), any(), any());
    }

    @Test
    void answer_alreadySubmitted_is409() throws Exception {
        when(sessionService.submitAnswer(eq(501L), eq(9001L), any(User.class), any(SubmitAnswerRequest.class)))
                .thenThrow(new IcelinkException(ErrorCode.ANSWER_ALREADY_SUBMITTED, "dup"));

        mockMvc.perform(post("/api/v1/teams/501/questions/9001/answer")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerText":"두 번째 제출"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANSWER_ALREADY_SUBMITTED"));
    }

    @Test
    void question_failed_showsReasonAndRetryable() throws Exception {
        when(sessionService.getQuestion(eq(501L), eq(9001L), any(User.class))).thenReturn(new TeamQuestionResponse(
                9001L, 3, QuestionType.AI_GENERATED, "q?", QuestionStatus.FAILED, AiFailureReason.LLM_TIMEOUT, true, null, NOW));

        mockMvc.perform(get("/api/v1/teams/501/questions/9001").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("LLM_TIMEOUT"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void questions_list_and_current() throws Exception {
        when(sessionService.listQuestions(eq(501L), any(User.class))).thenReturn(List.of(intro()));
        when(sessionService.getCurrent(eq(501L), any(User.class))).thenReturn(intro());

        mockMvc.perform(get("/api/v1/teams/501/questions").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNo").value(1));
        mockMvc.perform(get("/api/v1/teams/501/questions/current").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(8999));
    }

    @Test
    void retry_is202() throws Exception {
        when(sessionService.retry(eq(501L), eq(9001L), any(User.class)))
                .thenReturn(new SubmitAnswerResponse(9001L, "PROCESSING", 8, true));

        mockMvc.perform(post("/api/v1/teams/501/questions/9001/retry").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isAccepted());
    }

    @Test
    void summary_beforeFinish_is409() throws Exception {
        when(sessionService.summary(eq(501L), any(User.class)))
                .thenThrow(new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "not finished"));

        mockMvc.perform(get("/api/v1/teams/501/summary").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isConflict());
    }
}
