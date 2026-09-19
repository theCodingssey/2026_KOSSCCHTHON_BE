package com.kosscchthon.Icelink.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.ParticipantAccessChecker;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.team.TeamAccessChecker;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserService;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(SseController.class)
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class,
        SseEmitterRegistry.class, SseEventSerializer.class})
class SseControllerTest {

    private static final String KEY = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "호스트", NOW);

    @Autowired MockMvc mockMvc;
    @Autowired SseEmitterRegistry registry;
    @MockitoBean UserService userService;
    @MockitoBean RoomAccessChecker roomAccessChecker;
    @MockitoBean ParticipantAccessChecker participantAccessChecker;
    @MockitoBean TeamAccessChecker teamAccessChecker;
    @MockitoBean RoomEventRepository eventRepository;
    @MockitoBean SseParticipantView participantView;

    private Room room;

    @BeforeEach
    void setUp() throws Exception {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
        room = Room.create("K7M3PQ", USER, "t", "s", 4, List.of(), NOW, Duration.ofHours(24));
        Field f = Room.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(room, 12L);
    }

    @Test
    void hostStream_opensEventStream_andSendsConnectedEvent() throws Exception {
        when(roomAccessChecker.requireHost("K7M3PQ", KEY)).thenReturn(room);
        when(eventRepository.findFirstByRoomIdOrderByIdDesc(12L))
                .thenReturn(SseSubscriptionTest.event(42, 12L, null, RoomEventType.PARTICIPANT_JOINED));

        MvcResult result = mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/events")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("event:CONNECTED").contains("\"scope\":\"HOST\"").contains("\"lastEventId\":42");
        assertThat(registry.connectionCount(12L)).isGreaterThanOrEqualTo(1); // 컨텍스트가 테스트 간 공유되어 누적될 수 있다
    }

    @Test
    void hostStream_replaysMissedEventsAfterLastEventId() throws Exception {
        when(roomAccessChecker.requireHost("K7M3PQ", KEY)).thenReturn(room);
        when(eventRepository.findFirstByRoomIdOrderByIdDesc(12L))
                .thenReturn(SseSubscriptionTest.event(45, 12L, null, RoomEventType.PARTICIPANT_JOINED));
        when(eventRepository.findAllByRoomIdAndIdGreaterThanOrderByIdAsc(12L, 43L)).thenReturn(List.of(
                SseSubscriptionTest.event(44, 12L, null, RoomEventType.PARTICIPANT_JOINED),
                SseSubscriptionTest.event(45, 12L, 501L, RoomEventType.QUESTION_CREATED)));

        MvcResult result = mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/events")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .header("Last-Event-ID", "43")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("id:44").contains("event:PARTICIPANT_JOINED")
                .contains("id:45").contains("event:QUESTION_CREATED");
    }

    @Test
    void hostStream_forbiddenForNonHost() throws Exception {
        when(roomAccessChecker.requireHost("K7M3PQ", KEY)).thenThrow(new IcelinkException(ErrorCode.FORBIDDEN, "no"));

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/events").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void stream_acceptsUserKeyQueryParam_forClientsWithoutHeaderSupport() throws Exception {
        when(roomAccessChecker.requireHost("K7M3PQ", KEY)).thenReturn(room);
        when(eventRepository.findFirstByRoomIdOrderByIdDesc(12L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/events").param(UserKeyInterceptor.QUERY_PARAM, KEY))
                .andExpect(request().asyncStarted());
    }

    @Test
    void stream_withoutKey_is401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void broadcast_reachesOpenHostSubscription() throws Exception {
        when(roomAccessChecker.requireHost("K7M3PQ", KEY)).thenReturn(room);
        when(eventRepository.findFirstByRoomIdOrderByIdDesc(12L)).thenReturn(null);
        MvcResult result = mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/events").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(request().asyncStarted())
                .andReturn();

        RoomEventEntity event = SseSubscriptionTest.event(7, 12L, 501L, RoomEventType.TEAM_NAME_CHANGED);
        registry.broadcast(event, Map.of(), (sub, e) -> Map.of("name", "감자전사"));

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("id:7").contains("event:TEAM_NAME_CHANGED").contains("감자전사");
    }
}
