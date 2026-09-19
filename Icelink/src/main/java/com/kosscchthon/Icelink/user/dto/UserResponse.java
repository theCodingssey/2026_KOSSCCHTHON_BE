package com.kosscchthon.Icelink.user.dto;

import com.kosscchthon.Icelink.user.User;
import java.time.Instant;

public record UserResponse(String userKey, String name, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserKey(), user.getName(), user.getCreatedAt());
    }
}
