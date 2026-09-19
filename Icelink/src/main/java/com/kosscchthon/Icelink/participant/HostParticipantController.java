package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.participant.dto.HostParticipantResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주최자용 참가자 관리. 방의 host_user_id == X-User-Key 여야 한다. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/host/rooms/{code}/participants")
@RequiredArgsConstructor
@Tag(name = "Host Room", description = "주최자 전용 방 관리")
public class HostParticipantController {

    private final ParticipantService participantService;

    @GetMapping
    @Operation(summary = "참가자 목록", description = "LEFT 제외, 입장 순. 외향 점수·카테고리 포함.")
    public List<HostParticipantResponse> list(@PathVariable String code, @CurrentUser User host) {
        return participantService.listForHost(code, host);
    }

    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "참가자 강퇴", description = "WAITING 상태에서만. 대상은 LEFT 가 되고 재입장은 가능하다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "404", description = "PARTICIPATION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — 팀 빌딩 이후")
    })
    public void kick(@PathVariable String code, @PathVariable Long participantId, @CurrentUser User host) {
        participantService.kick(code, host, participantId);
    }
}
