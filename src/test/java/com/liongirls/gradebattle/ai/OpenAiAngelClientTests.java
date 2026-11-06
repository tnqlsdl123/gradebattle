package com.liongirls.gradebattle.ai;

import com.liongirls.gradebattle.domain.ai.client.OpenAiAngelClient;
import com.liongirls.gradebattle.domain.ai.dto.AngelAnalysisRequest;
import com.liongirls.gradebattle.domain.ai.exception.AiErrorCode;
import com.liongirls.gradebattle.global.config.openai.OpenAiProperties;
import com.liongirls.gradebattle.global.exception.GeneralException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiAngelClientTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private RestClient restClient;
    private OpenAiAngelClient client;
    private ValidatorFactory validation;

    @BeforeEach
    void prepare() {
        validation = Validation.buildDefaultValidatorFactory();
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new OpenAiAngelClient(restClient, new OpenAiProperties("test-key", "gpt-4.1-mini"), mapper, validation.getValidator());
    }

    @AfterEach
    void close() {
        validation.close();
    }

    @Test
    void sendsOnlyAnonymousCourseScoresAndCreditsWithStoreFalse() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    String raw = ((MockClientHttpRequest) request).getBodyAsString();
                    var body = mapper.readTree(raw);
                    assertThat(body.path("store").asBoolean()).isFalse();
                    assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
                    var input = mapper.readTree(body.path("input").asString());
                    assertThat(input.size()).isEqualTo(6);
                    assertThat(input.path("courseName").asString()).isEqualTo("분석 과목");
                    assertThat(input.has("credits")).isTrue();
                    for (String field : new String[]{"attendanceScore", "assignmentScore", "midtermScore", "finalScore"}) {
                        assertThat(input.has(field)).isTrue();
                    }
                    assertThat(raw).doesNotContain("PRIVATE-COURSE", "student@example.com", "targetScore", "userId", "password", "test-key");
                }).andRespond(withSuccess(envelope("""
                        {"problem":"과제 점수를 확인해요.","actions":["복습해요.","계획을 세워요."],"encouragement":"할 수 있어요!"}
                        """), MediaType.APPLICATION_JSON));
        assertThat(client.analyze(grades()).actions()).hasSize(2);
        server.verify();
    }

    @Test
    void missingKeyNeverSendsRequestAndPropertiesHideKey() {
        var missing = new OpenAiAngelClient(restClient, new OpenAiProperties("", "gpt-4.1-mini"), mapper, validation.getValidator());
        assertError(() -> missing.analyze(grades()), AiErrorCode.NOT_CONFIGURED);
        assertThat(new OpenAiProperties("PRIVATE-KEY", "model").toString()).doesNotContain("PRIVATE-KEY");
        server.verify();
    }

    @Test
    void hidesUpstreamAuthRateLimitAndServerErrorBodies() {
        for (var entry : Map.of(401, AiErrorCode.NOT_CONFIGURED, 429, AiErrorCode.RATE_LIMITED, 500, AiErrorCode.UNAVAILABLE).entrySet()) {
            server.reset();
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withStatus(HttpStatus.valueOf(entry.getKey())).body("PRIVATE-UPSTREAM-RESPONSE"));
            assertError(() -> client.analyze(grades()), entry.getValue());
            server.verify();
        }
    }

    @Test
    void convertsTimeoutToSafeError() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withException(new SocketTimeoutException("PRIVATE-TIMEOUT")));
        assertError(() -> client.analyze(grades()), AiErrorCode.TIMEOUT);
    }

    @Test
    void refusesIncompleteMalformedAndInvalidAdviceInsteadOfFakeSuccess() {
        for (String response : new String[]{"{}", "{\"status\":\"incomplete\"}", envelope("not-json"),
                envelope("{\"problem\":\"\",\"actions\":[],\"encouragement\":\"\"}")}) {
            server.reset();
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertError(() -> client.analyze(grades()), AiErrorCode.UNAVAILABLE);
        }
    }

    @Test
    void handlesModelRefusal() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"declined"}]}]}
                        """, MediaType.APPLICATION_JSON));
        assertError(() -> client.analyze(grades()), AiErrorCode.REFUSED);
    }

    private String envelope(String text) {
        return mapper.writeValueAsString(Map.of("status", "completed", "output", java.util.List.of(
                Map.of("type", "message", "content", java.util.List.of(Map.of("type", "output_text", "text", text))))));
    }

    private AngelAnalysisRequest grades() {
        return new AngelAnalysisRequest("PRIVATE-COURSE student@example.com", new BigDecimal("3"), new BigDecimal("100"),
                new BigDecimal("80"), new BigDecimal("50"), null, new BigDecimal("60"));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, AiErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeneralException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(expected);
            assertThat(error.getMessage()).doesNotContain("PRIVATE", "test-key");
            assertThat(error.getCause()).isNull();
        });
    }
}
