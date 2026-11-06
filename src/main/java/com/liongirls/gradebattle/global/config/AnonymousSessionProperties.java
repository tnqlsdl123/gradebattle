package com.liongirls.gradebattle.global.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.anonymous-session")
public record AnonymousSessionProperties(

        @Min(value = 1, message = "익명 세션 만료 기간은 1일 이상이어야 합니다.")
        long expirationDays,

        boolean cookieSecure
) {
}