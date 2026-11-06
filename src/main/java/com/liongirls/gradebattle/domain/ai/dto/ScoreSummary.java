package com.liongirls.gradebattle.domain.ai.dto;

import java.math.BigDecimal;

public record ScoreSummary(
        BigDecimal earnedScore,
        int completedWeight,
        int remainingWeight,
        BigDecimal maxPossibleScore,
        BigDecimal targetScore,
        BigDecimal requiredRemainingAverage,
        boolean targetReachable
) {
}
