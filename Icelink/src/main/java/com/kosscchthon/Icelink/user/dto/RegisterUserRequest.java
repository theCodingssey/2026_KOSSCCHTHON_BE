package com.kosscchthon.Icelink.user.dto;

import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "유저 등록 요청. 서버가 256-bit 난수 키(64자 hex)를 생성해 돌려준다.")
public record RegisterUserRequest(
        @Schema(description = "표시 이름 (1~12자)", example = "민수")
        @NotBlank
        @Size(max = User.NAME_MAX_LENGTH, message = "이름은 12자 이하여야 합니다")
        String name
) {
}
