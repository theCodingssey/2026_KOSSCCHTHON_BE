package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.participant.dto.JoinRoomRequest;
import com.kosscchthon.Icelink.participant.dto.JoinRoomResponse;
import com.kosscchthon.Icelink.participant.dto.ParticipantMeResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 참가자 관점 API. 참가 요청은 유저 인증만, 나머지는 이 방의 참가자여야 한다. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/rooms/{code}")
@RequiredArgsConstructor
@Tag(name = "Participant", description = "방 참가·내 상태·나가기")
public class ParticipantController {

    private final ParticipantService participantService;

    @PostMapping("/participants")
    @Operation(summary = "방 참가", description = "닉네임 생략 시 유저 이름. 이미 참가 중이면 200 으로 기존 정보를 돌려준다 (멱등).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신규 참가"),
            @ApiResponse(responseCode = "200", description = "이미 참가 중"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "ROOM_NOT_WAITING / ROOM_FULL / HOST_CANNOT_JOIN / ALREADY_IN_ANOTHER_ROOM / NICKNAME_DUPLICATED(suggestedNickname 포함)")
    })
    public ResponseEntity<JoinRoomResponse> join(@PathVariable String code, @CurrentUser User user,
                                                 @Valid @RequestBody(required = false) JoinRoomRequest request) {
        ParticipantService.JoinResult result = participantService.join(code, user,
                request == null ? JoinRoomRequest.empty() : request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @GetMapping("/me")
    @Operation(summary = "이 방에서의 내 상태", description = "참가·설문·팀 정보를 한 번에. 세부 화면 복원 기준.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — 이 방의 참가자가 아님")
    })
    public ParticipantMeResponse me(@PathVariable String code, @CurrentUser User user) {
        return participantService.getMe(code, user);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "방 나가기", description = "WAITING 상태에서만. 상태 LEFT. 이후 같은 방에 다시 참가할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — 팀 빌딩 이후")
    })
    public void leave(@PathVariable String code, @CurrentUser User user) {
        participantService.leave(code, user);
    }
}
