package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.auth.PublicApi;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.room.dto.CreateRoomRequest;
import com.kosscchthon.Icelink.room.dto.RoomPublicResponse;
import com.kosscchthon.Icelink.room.dto.RoomResponse;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 방 생성(유저)과 공개 조회(인증 없음). 주최자 전용은 {@link HostRoomController}. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "방 생성·공개 조회")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "방 생성", description = "요청한 유저가 주최자가 된다. 6자리 코드와 초대 링크가 발급된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201"),
            @ApiResponse(responseCode = "409", description = "ALREADY_IN_ANOTHER_ROOM — 진행 중인 방을 이미 주최 중")
    })
    public RoomResponse create(@CurrentUser User user, @Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(user, request);
    }

    @PublicApi
    @SecurityRequirements
    @GetMapping("/{code}")
    @Operation(summary = "방 공개 정보", description = "입장 전 코드 확인용. 인증 없음. 소문자 코드도 허용.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND")
    })
    public RoomPublicResponse getPublic(@PathVariable String code) {
        return roomService.getPublic(code);
    }
}
