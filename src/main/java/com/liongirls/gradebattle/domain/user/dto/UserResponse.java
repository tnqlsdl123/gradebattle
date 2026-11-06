package com.liongirls.gradebattle.domain.user.dto;

import com.liongirls.gradebattle.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long userId, String loginId, String nickname, int totalXp, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getLoginId(), user.getNickname(),
                user.getTotalXp(), user.getCreatedAt());
    }
}
