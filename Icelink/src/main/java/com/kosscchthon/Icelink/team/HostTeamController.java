package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.team.dto.HostTeamResponse;
import com.kosscchthon.Icelink.team.dto.TeamBuildingRequest;
import com.kosscchthon.Icelink.team.dto.TeamBuildingResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주최자용 팀 빌딩 실행·팀 목록. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/host/rooms/{code}")
@RequiredArgsConstructor
@Tag(name = "Host Room", description = "주최자 전용 방 관리")
public class HostTeamController {

    private final TeamBuildingService teamBuildingService;
    private final TeamQueryService teamQueryService;

    @PostMapping("/team-building")
    @Operation(summary = "팀 빌딩 실행",
            description = "참가를 마감하고 같은 카테고리끼리 외향 평균이 비슷하도록 팀을 만든다. "
                    + "방 WAITING → IN_PROGRESS. 설문 미완료자는 기본적으로 LATE 로 제외된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "409", description = "NOT_ENOUGH_PARTICIPANTS(설문 완료 2명 미만) / INVALID_STATE_TRANSITION(WAITING 아님)")
    })
    public TeamBuildingResponse build(@PathVariable String code, @CurrentUser User host,
                                      @RequestBody(required = false) TeamBuildingRequest request) {
        return teamBuildingService.build(code, host, request == null ? TeamBuildingRequest.defaults() : request);
    }

    @GetMapping("/teams")
    @Operation(summary = "팀 목록", description = "팀 번호 순. 팀명·상태·카테고리·외향 평균·팀원(점수 포함).")
    public List<HostTeamResponse> teams(@PathVariable String code, @CurrentUser User host) {
        return teamQueryService.listForHost(code, host);
    }
}
