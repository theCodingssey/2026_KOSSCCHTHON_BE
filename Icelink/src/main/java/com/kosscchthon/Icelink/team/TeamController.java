package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.team.dto.TeamDetailResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팀 조회. 세션 진행 API(시작·팀명·질문·답변)는 3.5 에서 이 컨트롤러에 추가된다. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE)
@RequiredArgsConstructor
@Tag(name = "Team", description = "팀 조회·세션 진행")
public class TeamController {

    private final TeamQueryService teamQueryService;

    @GetMapping("/teams/{teamId}")
    @Operation(summary = "팀 상세", description = "팀원 또는 그 방의 주최자만. members[].isMe 로 본인 표시.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — 팀원도 주최자도 아님"),
            @ApiResponse(responseCode = "404", description = "TEAM_NOT_FOUND")
    })
    public TeamDetailResponse team(@PathVariable Long teamId, @CurrentUser User user) {
        return teamQueryService.getTeam(teamId, user);
    }

    @GetMapping("/rooms/{code}/me/team")
    @Operation(summary = "내 팀", description = "배정된 팀 상세. 미배정이면 404 TEAM_NOT_FOUND → 프론트는 대기 화면 유지.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — 이 방의 참가자가 아님"),
            @ApiResponse(responseCode = "404", description = "TEAM_NOT_FOUND — 미배정")
    })
    public TeamDetailResponse myTeam(@PathVariable String code, @CurrentUser User user) {
        return teamQueryService.getMyTeam(code, user);
    }
}
