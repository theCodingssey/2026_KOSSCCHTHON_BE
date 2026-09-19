package com.kosscchthon.Icelink.survey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.survey.dto.SurveyResponse;
import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SurveyController.class)
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class SurveyControllerTest {

    private static final String KEY = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "민수", NOW);
    private static final String FULL_BODY = """
            {
              "personality": [
                {"no":1,"score":4},{"no":2,"score":5},{"no":3,"score":3},
                {"no":4,"score":4},{"no":5,"score":5},{"no":6,"score":3}
              ],
              "interestCategory": "GAME"
            }
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean SurveyService surveyService;

    @BeforeEach
    void authenticate() {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
    }

    @Test
    void submit_full_returnsSurveyDone() throws Exception {
        when(surveyService.submit(eq("K7M3PQ"), any(User.class), any(SurveySubmitRequest.class)))
                .thenReturn(new SurveyResponse(ParticipantStatus.SURVEY_DONE, true, true, 24, ExtroversionLevel.EXTROVERT,
                        InterestCategory.GAME, SurveyTestFixtures.answerList(4, 5, 3, 4, 5, 3)));

        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SURVEY_DONE"))
                .andExpect(jsonPath("$.extroversionScore").value(24))
                .andExpect(jsonPath("$.extroversionLevel").value("EXTROVERT"))
                .andExpect(jsonPath("$.interestCategory").value("GAME"))
                .andExpect(jsonPath("$.personality[0].no").value(1));

        ArgumentCaptor<SurveySubmitRequest> captor = ArgumentCaptor.forClass(SurveySubmitRequest.class);
        verify(surveyService).submit(eq("K7M3PQ"), any(User.class), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().personality()).hasSize(6);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().interestCategory()).isEqualTo(InterestCategory.GAME);
    }

    @Test
    void submit_categoryOnly_isAccepted() throws Exception {
        when(surveyService.submit(eq("K7M3PQ"), any(User.class), any(SurveySubmitRequest.class)))
                .thenReturn(new SurveyResponse(ParticipantStatus.JOINED, false, true, null, null, InterestCategory.FOOD, List.of()));

        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interestCategory":"FOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.extroversionScore").value((Object) null));
    }

    @Test
    void submit_wrongQuestionCount_is400BeforeService() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personality":[{"no":1,"score":4}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(surveyService, never()).submit(any(), any(), any());
    }

    @Test
    void submit_scoreOutOfRange_is400BeforeService() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personality":[
                                  {"no":1,"score":6},{"no":2,"score":5},{"no":3,"score":3},
                                  {"no":4,"score":4},{"no":5,"score":5},{"no":6,"score":3}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("personality[0].score"));
        verify(surveyService, never()).submit(any(), any(), any());
    }

    @Test
    void submit_unknownCategory_is400() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interestCategory":"MUSIC"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submit_withoutHeader_is401() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/K7M3PQ/me/survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_returnsSurvey() throws Exception {
        when(surveyService.get(eq("K7M3PQ"), any(User.class)))
                .thenReturn(new SurveyResponse(ParticipantStatus.JOINED, false, false, null, null, null, List.of()));

        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me/survey").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalityDone").value(false))
                .andExpect(jsonPath("$.personality").isEmpty());
    }
}
