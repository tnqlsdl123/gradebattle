package com.liongirls.gradebattle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(max = 72) String password
) {
}
