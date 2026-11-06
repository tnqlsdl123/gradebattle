package com.liongirls.gradebattle.domain.ai.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AngelAnalysisRequest(
        @NotBlank(message = "과목명을 입력해 주세요.")
        @Size(max = 100) String courseName,
        @DecimalMin("0.5") @DecimalMax("30") @Digits(integer = 2, fraction = 1) BigDecimal credits,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal attendanceScore,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal assignmentScore,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal midtermScore,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal finalScore,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal targetScore
) {
    @AssertTrue(message = "분석할 점수를 한 개 이상 입력해 주세요.")
    public boolean isAnyScoreProvided() {
        return attendanceScore != null || assignmentScore != null || midtermScore != null || finalScore != null;
    }

    public BigDecimal effectiveTargetScore() {
        return targetScore == null ? BigDecimal.valueOf(60) : targetScore;
    }
}
