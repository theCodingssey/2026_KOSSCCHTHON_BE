package com.kosscchthon.Icelink.team;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.team.dto.HostTeamResponse;
import com.kosscchthon.Icelink.team.dto.TeamBuildingRequest;
import com.kosscchthon.Icelink.team.dto.TeamBuildingResponse;
import com.kosscchthon.Icelink.team.dto.TeamDetailResponse;
import com.kosscchthon.Icelink.team.dto.TeamMemberView;
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

@WebMvcTest({TeamController.class, HostTeamController.class})
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class TeamControllerTest {

    private static final String KEY = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User USER = User.register(KEY, "민수", NOW);

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean TeamBuildingService teamBuildingService;
    @MockitoBean TeamQueryService teamQueryService;

    @BeforeEach
    void authenticate() {
        when(userService.authenticate(KEY)).thenReturn(Optional.of(USER));
    }

    private static TeamDetailResponse detail() {
        return new TeamDetailResponse(501L, 1, "1팀", true, TeamStatus.NOT_STARTED, InterestCategory.GAME, false, 0, 15,
                List.of(new TeamMemberView(101L, "민수", true, null, null), new TeamMemberView(102L, "지현", false, null, null)),
                null, List.of(), null, null);
    }

    @Test
    void teamBuilding_withoutBody_usesDefaults_andReturnsResult() throws Exception {
        when(teamBuildingService.build(eq("K7M3PQ"), any(User.class), any(TeamBuildingRequest.class)))
                .thenReturn(new TeamBuildingResponse(RoomStatus.IN_PROGRESS, 2, 8, 1,
                        List.of(new TeamBuildingResponse.CategoryGroupView(InterestCategory.GAME, 8, 2, false)),
                        List.of(new TeamBuildingResponse.BuiltTeamView(501L, 1, "1팀", InterestCategory.GAME, false, 18.5, List.of()))));

        mockMvc.perform(post("/api/v1/host/rooms/K7M3PQ/team-building").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.teamCount").value(2))
                .andExpect(jsonPath("$.lateCount").value(1))
                .andExpect(jsonPath("$.categoryGroups[0].category").value("GAME"))
                .andExpect(jsonPath("$.teams[0].name").value("1팀"));

        ArgumentCaptor<TeamBuildingRequest> captor = ArgumentCaptor.forClass(TeamBuildingRequest.class);
        verify(teamBuildingService).build(eq("K7M3PQ"), any(User.class), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().includeIncomplete()).isFalse();
    }

    @Test
    void teamBuilding_withIncludeIncomplete_passesFlag() throws Exception {
        when(teamBuildingService.build(eq("K7M3PQ"), any(User.class), any(TeamBuildingRequest.class)))
                .thenReturn(new TeamBuildingResponse(RoomStatus.IN_PROGRESS, 1, 3, 0, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/host/rooms/K7M3PQ/team-building")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"includeIncompleteSurvey":true}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TeamBuildingRequest> captor = ArgumentCaptor.forClass(TeamBuildingRequest.class);
        verify(teamBuildingService).build(eq("K7M3PQ"), any(User.class), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().includeIncomplete()).isTrue();
    }

    @Test
    void teamBuilding_notEnough_is409() throws Exception {
        when(teamBuildingService.build(eq("K7M3PQ"), any(User.class), any(TeamBuildingRequest.class)))
                .thenThrow(new IcelinkException(ErrorCode.NOT_ENOUGH_PARTICIPANTS, "few"));

        mockMvc.perform(post("/api/v1/host/rooms/K7M3PQ/team-building").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_PARTICIPANTS"));
    }

    @Test
    void hostTeams_returnsList() throws Exception {
        when(teamQueryService.listForHost(eq("K7M3PQ"), any(User.class))).thenReturn(List.of(
                new HostTeamResponse(501L, 1, "1팀", TeamStatus.NOT_STARTED, InterestCategory.GAME, false, 18.5, 0, null,
                        List.of(new TeamMemberView(101L, "민수", null, 24, InterestCategory.GAME)), null, null)));

        mockMvc.perform(get("/api/v1/host/rooms/K7M3PQ/teams").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(501))
                .andExpect(jsonPath("$[0].extroversionAvg").value(18.5))
                .andExpect(jsonPath("$[0].members[0].extroversionScore").value(24));
    }

    @Test
    void teamDetail_returnsMembersWithIsMe() throws Exception {
        when(teamQueryService.getTeam(eq(501L), any(User.class))).thenReturn(detail());

        mockMvc.perform(get("/api/v1/teams/501").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("1팀"))
                .andExpect(jsonPath("$.isDefaultName").value(true))
                .andExpect(jsonPath("$.questionLimit").value(15))
                .andExpect(jsonPath("$.members[0].isMe").value(true))
                .andExpect(jsonPath("$.members[1].isMe").value(false))
                .andExpect(jsonPath("$.currentQuestion").value((Object) null));
    }

    @Test
    void teamDetail_forbidden_is403() throws Exception {
        when(teamQueryService.getTeam(eq(501L), any(User.class)))
                .thenThrow(new IcelinkException(ErrorCode.FORBIDDEN, "no"));

        mockMvc.perform(get("/api/v1/teams/501").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void myTeam_unassigned_is404() throws Exception {
        when(teamQueryService.getMyTeam(eq("K7M3PQ"), any(User.class)))
                .thenThrow(new IcelinkException(ErrorCode.TEAM_NOT_FOUND, "not yet"));

        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me/team").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void myTeam_assigned_returnsDetail() throws Exception {
        when(teamQueryService.getMyTeam(eq("K7M3PQ"), any(User.class))).thenReturn(detail());

        mockMvc.perform(get("/api/v1/rooms/K7M3PQ/me/team").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(501))
                .andExpect(jsonPath("$.category").value("GAME"));
    }
}
