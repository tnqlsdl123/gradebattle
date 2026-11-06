package com.liongirls.gradebattle.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalysisConsentRequest(@NotNull Boolean consent, @NotBlank String noticeVersion,
                                     @NotBlank String gradeVersion) {
}
