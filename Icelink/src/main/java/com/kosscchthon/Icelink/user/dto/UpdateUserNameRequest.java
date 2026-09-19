package com.kosscchthon.Icelink.user.dto;

import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserNameRequest(
        @Schema(description = "새 표시 이름 (1~12자). 키는 바뀌지 않는다", example = "민수짱")
        @NotBlank
        @Size(max = User.NAME_MAX_LENGTH, message = "이름은 12자 이하여야 합니다")
        String name
) {
}
