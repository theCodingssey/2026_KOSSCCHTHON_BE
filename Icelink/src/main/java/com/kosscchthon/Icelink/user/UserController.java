package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.auth.PublicApi;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.user.dto.MeResponse;
import com.kosscchthon.Icelink.user.dto.RegisterUserRequest;
import com.kosscchthon.Icelink.user.dto.UpdateUserNameRequest;
import com.kosscchthon.Icelink.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 등록·조회. 회원가입/로그인 없음, 클라이언트 생성 키 기반")
public class UserController {

    private final UserService userService;

    @PublicApi
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "유저 등록", description = "이름만 보내면 서버가 256-bit 난수 키(64자 hex)를 생성해 돌려준다. 클라이언트는 이 키를 저장한다. "
            + "같은 이름이 몇 명이든 등록된다. 409 USER_KEY_CONFLICT 는 PK 충돌(사실상 발생하지 않음)이며 다시 시도하면 된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
            @ApiResponse(responseCode = "409", description = "USER_KEY_CONFLICT")
    })
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return userService.register(request);
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 + 진행 중인 방", description = "앱 기동 시 첫 호출. activeRoom 으로 첫 화면을 결정한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "401", description = "USER_KEY_REQUIRED / USER_NOT_FOUND")
    })
    public MeResponse me(@CurrentUser User user) {
        return userService.getMe(user.getUserKey());
    }

    @PatchMapping("/me")
    @Operation(summary = "이름 변경", description = "키는 유지된다. 이미 참가 중인 방의 닉네임은 바뀌지 않는다.")
    public UserResponse rename(@CurrentUser User user, @Valid @RequestBody UpdateUserNameRequest request) {
        return userService.rename(user.getUserKey(), request.name());
    }
}
