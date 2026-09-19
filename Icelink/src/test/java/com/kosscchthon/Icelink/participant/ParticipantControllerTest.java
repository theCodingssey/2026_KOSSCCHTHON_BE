package com.kosscchthon.Icelink.participant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.ParticipantService.JoinResult;
import com.kosscchthon.Icelink.participant.dto.HostParticipantResponse;
import com.kosscchthon.Icelink.participant.dto.JoinRoomRequest;
import com.kosscchthon.Icelink.participant.dto.JoinRoomResponse;
import com.kosscchthon.Icelink.participant.dto.ParticipantMeResponse;
import com.kosscchthon.Icelink.participant.dto.RoomBrief;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@WebMvcTest({ParticipantController.class, HostParticipantController.class})
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class ParticipantControllerTest {

    private static final String KEY = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "민수", NOW);
    private static final RoomBrief ROOM = new RoomBrief(12L, "K7M3PQ", "제목", RoomStatus.WAITING, 4);

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean ParticipantService participantService;

    @BeforeEach
    void authenticate() {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
    }

    @Test
    void join_newParticipant_is201() throws Exception {
        when(participantService.join(eq("K7M3PQ"), any(User.class), any(JoinRoomRequest.class)))
                .thenReturn(new JoinResult(new JoinRoomResponse(101L, "민수", ParticipantStatus.JOINED, ROOM), true));

        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"민수"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantId").value(101))
                .andExpect(jsonPath("$.room.code").value("K7M3PQ"));
    }

    @Test
    void join_withoutBody_isAcceptedAndPassesEmptyRequest() throws Exception {
        when(participantService.join(eq("K7M3PQ"), any(User.class), any(JoinRoomRequest.class)))
                .thenReturn(new JoinResult(new JoinRoomResponse(101L, "민수", ParticipantStatus.JOINED, ROOM), true));

        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isCreated());

        ArgumentCaptor<JoinRoomRequest> captor = ArgumentCaptor.forClass(JoinRoomRequest.class);
        verify(participantService).join(eq("K7M3PQ"), any(User.class), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().nickname()).isNull();
    }

    @Test
    void join_alreadyJoined_is200() throws Exception {
        when(participantService.join(eq("K7M3PQ"), any(User.class), any(JoinRoomRequest.class)))
                .thenReturn(new JoinResult(new JoinRoomResponse(101L, "민수", ParticipantStatus.SURVEY_DONE, ROOM), false));

        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SURVEY_DONE"));
    }

    @Test
    void join_duplicateNickname_is409WithSuggestion() throws Exception {
        when(participantService.join(eq("K7M3PQ"), any(User.class), any(JoinRoomRequest.class)))
                .thenThrow(new IcelinkException(ErrorCode.NICKNAME_DUPLICATED, "dup", Map.of("suggestedNickname", "민수2")));

        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"민수"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"))
                .andExpect(jsonPath("$.suggestedNickname").value("민수2"));
    }

    @Test
    void join_nicknameTooLong_is400BeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"열세글자가넘는닉네임입니다요"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(participantService, never()).join(any(), any(), any());
    }

    @Test
    void join_withoutHeader_is401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/K7M3PQ/participants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_KEY_REQUIRED"));
    }

    @Test
    void me_returnsAggregate() throws Exception {
        when(participantService.getMe(eq("K7M3PQ"), any(User.class))).thenReturn(new ParticipantMeResponse(
                101L, KEY, "민수", ParticipantStatus.JOINED,
                new ParticipantMeResponse.RoomView(12L, "K7M3PQ", "제목", RoomStatus.WAITING, 4, 7, null),
                new ParticipantMeResponse.SurveyView(false, false, null, null),
                null));

        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.participantCount").value(7))
                .andExpect(jsonPath("$.room.finalQuestions").value((Object) null))
                .andExpect(jsonPath("$.survey.personalityDone").value(false))
                .andExpect(jsonPath("$.team").value((Object) null));
    }

    @Test
    void me_notParticipant_is403() throws Exception {
        when(participantService.getMe(eq("K7M3PQ"), any(User.class)))
                .thenThrow(new IcelinkException(ErrorCode.FORBIDDEN, "no"));

        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void leave_is204() throws Exception {
        mockMvc.perform(delete("/api/v1/rooms/K7M3PQ/me").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNoContent());
        verify(participantService).leave(eq("K7M3PQ"), any(User.class));
    }

    @Test
    void hostList_returnsArray() throws Exception {
        when(participantService.listForHost(eq("K7M3PQ"), any(User.class))).thenReturn(List.of(
                new HostParticipantResponse(101L, "민수", ParticipantStatus.SURVEY_DONE, null, null, 24, InterestCategory.GAME, NOW)));

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/participants").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantId").value(101))
                .andExpect(jsonPath("$[0].extroversionScore").value(24))
                .andExpect(jsonPath("$[0].interestCategory").value("GAME"));
    }

    @Test
    void kick_is204() throws Exception {
        mockMvc.perform(delete("/api/v1/host/rooms/K7M3PQ/participants/101").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNoContent());
        verify(participantService).kick(eq("K7M3PQ"), any(User.class), eq(101L));
    }

    @Test
    void kick_afterTeamBuilding_is409() throws Exception {
        org.mockito.Mockito.doThrow(new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "late"))
                .when(participantService).kick(eq("K7M3PQ"), any(User.class), eq(101L));

        mockMvc.perform(delete("/api/v1/host/rooms/K7M3PQ/participants/101").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
}
