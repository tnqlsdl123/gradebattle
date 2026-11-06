package com.liongirls.gradebattle.ai;

import com.liongirls.gradebattle.domain.ai.dto.AngelAnalysisRequest;
import com.liongirls.gradebattle.domain.ai.service.ScoreCalculator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTests {
    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void calculatesWeightedFinalScore() {
        var result = calculator.calculate(request("100", "80", "70", "90", "60"));
        assertThat(result.earnedScore()).isEqualByComparingTo("84");
        assertThat(result.completedWeight()).isEqualTo(100);
        assertThat(result.remainingWeight()).isZero();
        assertThat(result.requiredRemainingAverage()).isNull();
        assertThat(result.targetReachable()).isTrue();
    }

    @Test
    void distinguishesUnconfirmedExamFromZeroAndCalculatesRecovery() {
        var pending = calculator.calculate(request("100", "80", "50", null, "60"));
        assertThat(pending.earnedScore()).isEqualByComparingTo("51");
        assertThat(pending.completedWeight()).isEqualTo(70);
        assertThat(pending.remainingWeight()).isEqualTo(30);
        assertThat(pending.maxPossibleScore()).isEqualByComparingTo("81");
        assertThat(pending.requiredRemainingAverage()).isEqualByComparingTo("30");
        var zero = calculator.calculate(request("100", "80", "50", "0", "60"));
        assertThat(zero.remainingWeight()).isZero();
        assertThat(zero.targetReachable()).isFalse();
    }

    @Test
    void reportsUnreachableTargetRatherThanPromisingSuccess() {
        var result = calculator.calculate(request("0", "0", "0", null, "60"));
        assertThat(result.targetReachable()).isFalse();
        assertThat(result.requiredRemainingAverage()).isEqualByComparingTo("200");
    }

    @Test
    void roundsRequiredAverageUpAndNeverRequiresNegativeScore() {
        var result = calculator.calculate(request("50", null, null, null, "60"));
        assertThat(result.requiredRemainingAverage()).isEqualByComparingTo("62.50");
        var repeating = calculator.calculate(request("100", "100", "60", null, "60"));
        assertThat(repeating.requiredRemainingAverage()).isEqualByComparingTo("6.67");
        var reached = calculator.calculate(request("100", "100", "100", null, "60"));
        assertThat(reached.requiredRemainingAverage()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsMissingScoresAndScoresOutOfRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request(null, null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(request("-1", null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(request("101", null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(request("50.001", null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(request("0", null, null, null, null))).isEmpty();
        }
        assertThat(calculator.calculate(request("0", null, null, null, null)).targetScore())
                .isEqualByComparingTo("60");
    }

    private AngelAnalysisRequest request(String attendance, String assignment, String midterm, String finalScore, String target) {
        return new AngelAnalysisRequest("테스트 과목", number("3"), number(attendance), number(assignment), number(midterm),
                number(finalScore), number(target));
    }

    private BigDecimal number(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
