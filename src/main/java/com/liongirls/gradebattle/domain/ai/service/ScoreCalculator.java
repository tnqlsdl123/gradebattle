package com.liongirls.gradebattle.domain.ai.service;

import com.liongirls.gradebattle.domain.ai.dto.AngelAnalysisRequest;
import com.liongirls.gradebattle.domain.ai.dto.ScoreSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ScoreCalculator {
    public ScoreSummary calculate(AngelAnalysisRequest request) {
        BigDecimal[] scores = {request.attendanceScore(), request.assignmentScore(), request.midtermScore(), request.finalScore()};
        int[] weights = {20, 20, 30, 30};
        BigDecimal earned = BigDecimal.ZERO;
        int completed = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null) {
                earned = earned.add(scores[i].multiply(BigDecimal.valueOf(weights[i])).movePointLeft(2));
                completed += weights[i];
            }
        }
        int remaining = 100 - completed;
        BigDecimal maximum = earned.add(BigDecimal.valueOf(remaining));
        BigDecimal target = request.effectiveTargetScore();
        BigDecimal required = remaining == 0 ? null : target.subtract(earned).max(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(remaining), 2, RoundingMode.CEILING);
        return new ScoreSummary(earned.setScale(2, RoundingMode.HALF_UP), completed, remaining,
                maximum.setScale(2, RoundingMode.HALF_UP), target, required, maximum.compareTo(target) >= 0);
    }
}
