package com.liongirls.gradebattle.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AngelAdvice(
        @NotBlank @Size(max = 600) String problem,
        @NotNull @Size(min = 2, max = 3) List<@NotBlank @Size(max = 300) String> actions,
        @NotBlank @Size(max = 300) String encouragement
) {
}
