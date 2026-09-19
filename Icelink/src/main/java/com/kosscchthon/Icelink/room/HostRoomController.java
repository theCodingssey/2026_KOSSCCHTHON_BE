package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.room.dto.FinalQuestionsResponse;
import com.kosscchthon.Icelink.room.dto.FinishRoomResponse;
import com.kosscchthon.Icelink.room.dto.RoomDetailResponse;
import com.kosscchthon.Icelink.room.dto.RoomResponse;
import com.kosscchthon.Icelink.room.dto.UpdateFinalQuestionsRequest;
import com.kosscchthon.Icelink.room.dto.UpdateRoomRequest;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주최자 전용 방 관리. 모든 요청은 방의 host_user_id == X-User-Key 여야 한다 (아니면 403). */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/host/rooms/{code}")
@RequiredArgsConstructor
@Tag(name = "Host Room", description = "주최자 전용 방 관리")
public class HostRoomController {

    private final RoomService roomService;

    @GetMapping
    @Operation(summary = "방 상세 (주최자 대시보드)", description = "방 정보 + 참가자 현황 + 팀 목록을 한 번에 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — 주최자 아님"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    })
    public RoomDetailResponse detail(@PathVariable String code, @CurrentUser User user) {
        return roomService.getHostDetail(code, user);
    }

    @PatchMapping
    @Operation(summary = "방 설정 수정", description = "제목·상황 설명·팀 인원. WAITING 상태에서만. 보낸 필드만 변경.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — WAITING 아님")
    })
    public RoomResponse update(@PathVariable String code, @CurrentUser User user,
                               @Valid @RequestBody UpdateRoomRequest request) {
        return roomService.updateSettings(code, user, request);
    }

    @PutMapping("/final-questions")
    @Operation(summary = "마무리 질문 목록 수정", description = "종료 전까지 언제든 가능. 목록 전체를 덮어쓴다 (0~5개).")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — 이미 종료")
    })
    public FinalQuestionsResponse updateFinalQuestions(@PathVariable String code, @CurrentUser User user,
                                                       @Valid @RequestBody UpdateFinalQuestionsRequest request) {
        return roomService.updateFinalQuestions(code, user, request);
    }

    @PostMapping("/finish")
    @Operation(summary = "아이스브레이킹 종료",
            description = "방과 모든 팀을 FINISHED 로 전이하고 마무리 질문 목록을 참가자에게 전파한다. 멱등.")
    public FinishRoomResponse finish(@PathVariable String code, @CurrentUser User user) {
        return roomService.finish(code, user);
    }
}
