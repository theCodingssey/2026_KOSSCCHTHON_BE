package com.kosscchthon.Icelink.room;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.room.dto.CreateRoomRequest;
import com.kosscchthon.Icelink.room.dto.FinalQuestionsResponse;
import com.kosscchthon.Icelink.room.dto.FinishRoomResponse;
import com.kosscchthon.Icelink.room.dto.ParticipantCounts;
import com.kosscchthon.Icelink.room.dto.RoomDetailResponse;
import com.kosscchthon.Icelink.room.dto.RoomPublicResponse;
import com.kosscchthon.Icelink.room.dto.RoomResponse;
import com.kosscchthon.Icelink.room.dto.UpdateFinalQuestionsRequest;
import com.kosscchthon.Icelink.room.dto.UpdateRoomRequest;
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

@WebMvcTest({RoomController.class, HostRoomController.class})
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class RoomControllerTest {

    private static final String KEY = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "민수", NOW);

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean RoomService roomService;

    @BeforeEach
    void authenticate() {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
    }

    private static RoomResponse roomResponse() {
        return new RoomResponse(12L, "K7M3PQ", new RoomResponse.HostSummary(KEY, "민수"),
                "https://icelink.app/join/K7M3PQ", "icelink://join?code=K7M3PQ",
                "제목", "상황", 4, List.of("q1"), RoomStatus.WAITING, NOW, NOW.plusSeconds(86400), null);
    }

    @Test
    void createRoom_requiresAuth_andReturns201() throws Exception {
        when(roomService.create(any(User.class), any(CreateRoomRequest.class))).thenReturn(roomResponse());

        mockMvc.perform(post("/api/v1/rooms")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","situation":"상황","teamSize":4,"finalQuestions":["q1"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("K7M3PQ"))
                .andExpect(jsonPath("$.host.name").value("민수"))
                .andExpect(jsonPath("$.inviteUrl").value("https://icelink.app/join/K7M3PQ"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void createRoom_withoutHeader_is401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","situation":"상황","teamSize":4}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_KEY_REQUIRED"));
        verify(roomService, never()).create(any(), any());
    }

    @Test
    void createRoom_validation_teamSizeOutOfRange_andTooManyFinalQuestions() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","situation":"상황","teamSize":11,"finalQuestions":["1","2","3","4","5","6"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createRoom_conflictWhenAlreadyHosting() throws Exception {
        when(roomService.create(any(), any()))
                .thenThrow(new IcelinkException(ErrorCode.ALREADY_IN_ANOTHER_ROOM, "already"));

        mockMvc.perform(post("/api/v1/rooms")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","situation":"상황","teamSize":4}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_ANOTHER_ROOM"));
    }

    @Test
    void getPublicRoom_isPublic_noHeaderNeeded() throws Exception {
        when(roomService.getPublic("k7m3pq"))
                .thenReturn(new RoomPublicResponse("K7M3PQ", "제목", RoomStatus.WAITING, 4, 17, true));

        mockMvc.perform(get("/api/v1/rooms/k7m3pq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M3PQ"))
                .andExpect(jsonPath("$.participantCount").value(17))
                .andExpect(jsonPath("$.joinable").value(true));
        verify(userService, never()).authenticate(any());
    }

    @Test
    void getPublicRoom_unknown_is404() throws Exception {
        when(roomService.getPublic("XXXXXX")).thenThrow(new IcelinkException(ErrorCode.ROOM_NOT_FOUND, "nf"));

        mockMvc.perform(get("/api/v1/rooms/XXXXXX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void hostDetail_returnsAggregate() throws Exception {
        when(roomService.getHostDetail(eq("K7M3PQ"), any(User.class))).thenReturn(new RoomDetailResponse(
                12L, "K7M3PQ", "제목", "상황", 4, List.of("q1"), RoomStatus.IN_PROGRESS,
                "https://icelink.app/join/K7M3PQ", "icelink://join?code=K7M3PQ",
                new ParticipantCounts(3, 0, 17, 1, 21), List.of(), List.of(), NOW, NOW.plusSeconds(86400), null));

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(21))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.participants").isArray())
                .andExpect(jsonPath("$.teams").isArray());
    }

    @Test
    void hostDetail_forbiddenForNonHost() throws Exception {
        when(roomService.getHostDetail(eq("K7M3PQ"), any(User.class)))
                .thenThrow(new IcelinkException(ErrorCode.FORBIDDEN, "not host"));

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void patchRoom_passesPartialBody() throws Exception {
        when(roomService.updateSettings(eq("K7M3PQ"), any(User.class), any(UpdateRoomRequest.class))).thenReturn(roomResponse());

        mockMvc.perform(patch("/api/v1/host/rooms/K7M3PQ")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamSize":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("K7M3PQ"));
    }

    @Test
    void putFinalQuestions_returnsList() throws Exception {
        when(roomService.updateFinalQuestions(eq("K7M3PQ"), any(User.class), any(UpdateFinalQuestionsRequest.class)))
                .thenReturn(new FinalQuestionsResponse(List.of("a", "b"), NOW));

        mockMvc.perform(put("/api/v1/host/rooms/K7M3PQ/final-questions")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finalQuestions":["a","b"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalQuestions[1]").value("b"));
    }

    @Test
    void putFinalQuestions_rejectsBlankElement() throws Exception {
        mockMvc.perform(put("/api/v1/host/rooms/K7M3PQ/final-questions")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finalQuestions":["a",""]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(roomService, never()).updateFinalQuestions(any(), any(), any());
    }

    @Test
    void finish_returnsFinalQuestions() throws Exception {
        when(roomService.finish(eq("K7M3PQ"), any(User.class)))
                .thenReturn(new FinishRoomResponse(RoomStatus.FINISHED, NOW, 5, List.of("q1")));

        mockMvc.perform(post("/api/v1/host/rooms/K7M3PQ/finish").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("FINISHED"))
                .andExpect(jsonPath("$.finishedTeamCount").value(5))
                .andExpect(jsonPath("$.finalQuestions[0]").value("q1"));
    }
}
