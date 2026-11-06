package com.liongirls.gradebattle.ai;

import com.jayway.jsonpath.JsonPath;
import com.liongirls.gradebattle.domain.ai.client.OpenAiAngelClient;
import com.liongirls.gradebattle.domain.ai.dto.AngelAdvice;
import com.liongirls.gradebattle.domain.ai.exception.AiErrorCode;
import com.liongirls.gradebattle.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AngelApiTests {
    private static final String GRADES = """
            {"courseName":"가상과목-PRIVATE-MARKER","credits":3,"attendanceScore":100,"assignmentScore":80,
            "midtermScore":50,"finalScore":null,"targetScore":60}
            """;
    @Autowired MockMvc mvc;
    @MockitoBean OpenAiAngelClient client;

    @BeforeEach
    void prepare() {
        when(client.analyze(any())).thenReturn(new AngelAdvice("가상 분석", List.of("복습하기", "계획 세우기"), "가상 응원"));
    }

    @Test
    void requiresLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/ai/grades/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/analyze").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(consent(true, "none"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/analyze").with(user("1")).contentType(MediaType.APPLICATION_JSON)
                .content(consent(true, "none"))).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test
    void noticeAndSavingGradesNeverCallOpenAi() throws Exception {
        mvc.perform(get("/api/ai/consent").with(user("1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.noticeVersion").value("angel-v1"));
        var session = new MockHttpSession();
        save(session);
        mvc.perform(get("/api/ai/grades/me").with(user("1")).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.grades.credits").value(3));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMissingFalseAndOutdatedConsent() throws Exception {
        var session = new MockHttpSession();
        String version = save(session);
        for (String body : new String[]{"{}", consent(false, version),
                consent(true, version).replace("angel-v1", "old-notice"), consent(true, "old-grades")}) {
            mvc.perform(post("/api/ai/analyze").with(user("1")).with(csrf()).session(session)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(client);
    }

    @Test
    void changedGradesRequireFreshConsentVersion() throws Exception {
        var session = new MockHttpSession();
        String previous = save(session);
        save(session);
        mvc.perform(post("/api/ai/analyze").with(user("1")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(consent(true, previous)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }

    @Test
    void anotherUserCannotReadOrAnalyzeGradesEvenUsingSameSession() throws Exception {
        var session = new MockHttpSession();
        String version = save(session);
        mvc.perform(get("/api/ai/grades/me").with(user("2")).session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/analyze").with(user("2")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(consent(true, version)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test
    void analyzesOwnGradesWithoutLoggingPayloadOrAdvice(CapturedOutput output) throws Exception {
        var session = new MockHttpSession();
        String version = save(session);
        mvc.perform(post("/api/ai/analyze").with(user("1")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(consent(true, version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.score.earnedScore").value(51))
                .andExpect(jsonPath("$.result.score.remainingWeight").value(30))
                .andExpect(jsonPath("$.result.advice.problem").value("가상 분석"));
        verify(client, times(1)).analyze(any());
        assertThat(output.getAll()).doesNotContain("PRIVATE-MARKER", "가상 분석", "가상 응원");
    }

    @Test
    void rejectsInvalidGradesBeforeExternalCallAndHidesFailureDetails() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(put("/api/ai/grades/me").with(user("1")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(GRADES.replace("100", "101")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
        String version = save(session);
        when(client.analyze(any())).thenThrow(new GeneralException(AiErrorCode.UNAVAILABLE));
        mvc.perform(post("/api/ai/analyze").with(user("1")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(consent(true, version)))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_502"))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    private String save(MockHttpSession session) throws Exception {
        String response = mvc.perform(put("/api/ai/grades/me").with(user("1")).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(GRADES)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.result.gradeVersion");
    }

    private String consent(boolean accepted, String version) {
        return "{\"consent\":" + accepted + ",\"noticeVersion\":\"angel-v1\",\"gradeVersion\":\"" + version + "\"}";
    }
}
