package com.liongirls.gradebattle.domain.ai.client;

import com.liongirls.gradebattle.domain.ai.dto.AngelAdvice;
import com.liongirls.gradebattle.domain.ai.dto.AngelAnalysisRequest;
import com.liongirls.gradebattle.domain.ai.exception.AiErrorCode;
import com.liongirls.gradebattle.global.config.openai.OpenAiProperties;
import com.liongirls.gradebattle.global.exception.GeneralException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiAngelClient {
    private static final String INSTRUCTIONS = """
            너는 학습 게임의 친절한 대천사다. 한국어 존댓말로 간결하게 답한다.
            사용자 JSON에는 익명화된 과목명, 성적, 이수학점만 있다. 각 점수는 100점 만점이다.
            고정 배점은 출석 20%, 과제 20%, 중간 30%, 기말 30%다. 학점은 성적의 배점이 아니다.
            null은 미응시 또는 미확정이다. 미확정 점수를 0점이나 실패로 해석하지 않는다.
            problem: 현재 취약한 항목을 근거와 함께 1~2문장으로 설명한다.
            actions: 학생이 지금 할 수 있는 구체적인 행동 2~3개를 각각 1문장으로 제안한다.
            encouragement: 대천사다운 따뜻한 응원 1문장을 쓴다.
            결석 원인, 과제 마감일, 상대평가 학점, 합격 기준처럼 주어지지 않은 사실을 지어내지 않는다.
            확정된 시험 점수를 바꿀 수 있다고 하거나 성적 향상을 보장하지 않는다.
            모욕·비난·인격평가를 하지 않는다. 각 문자열은 200자 이내로 작성한다.
            """;
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object", "additionalProperties", false,
            "properties", Map.of("problem", Map.of("type", "string"),
                    "actions", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 2, "maxItems", 3),
                    "encouragement", Map.of("type", "string")),
            "required", List.of("problem", "actions", "encouragement"));

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper mapper;
    private final Validator validator;

    public OpenAiAngelClient(@Qualifier("openAiRestClient") RestClient restClient,
                             OpenAiProperties properties, ObjectMapper mapper, Validator validator) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.validator = validator;
    }

    public AngelAdvice analyze(AngelAnalysisRequest request) {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.model())) {
            throw new GeneralException(AiErrorCode.NOT_CONFIGURED);
        }
        try {
            // 원본 DTO, DB 엔티티, 세션, 인증 객체를 외부 요청에 직렬화하지 않는다.
            var allowed = new OutboundGrades("분석 과목", request.credits(), request.attendanceScore(),
                    request.assignmentScore(), request.midtermScore(), request.finalScore());
            var body = Map.of("model", properties.model(), "store", false, "max_output_tokens", 1200,
                    "instructions", INSTRUCTIONS, "input", mapper.writeValueAsString(allowed),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", "angel_advice",
                            "strict", true, "schema", SCHEMA)));
            JsonNode response = restClient.post().uri("/responses").body(body).retrieve().body(JsonNode.class);
            return parseAdvice(response);
        } catch (RestClientResponseException e) {
            // 외부 응답 본문/예외를 로그나 클라이언트에 노출하지 않는다.
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) throw new GeneralException(AiErrorCode.NOT_CONFIGURED);
            if (status == 429) throw new GeneralException(AiErrorCode.RATE_LIMITED);
            throw new GeneralException(AiErrorCode.UNAVAILABLE);
        } catch (ResourceAccessException e) {
            throw new GeneralException(AiErrorCode.TIMEOUT);
        } catch (RestClientException | JacksonException e) {
            throw new GeneralException(AiErrorCode.UNAVAILABLE);
        }
    }

    private AngelAdvice parseAdvice(JsonNode response) {
        if (response == null || !"completed".equals(response.path("status").asString())) {
            throw new GeneralException(AiErrorCode.UNAVAILABLE);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asString())) continue;
            for (JsonNode content : item.path("content")) {
                String type = content.path("type").asString();
                if ("refusal".equals(type)) throw new GeneralException(AiErrorCode.REFUSED);
                if ("output_text".equals(type)) text.append(content.path("text").asString(""));
            }
        }
        if (text.isEmpty()) throw new GeneralException(AiErrorCode.UNAVAILABLE);
        AngelAdvice advice = mapper.readValue(text.toString(), AngelAdvice.class);
        if (advice == null || !validator.validate(advice).isEmpty()) throw new GeneralException(AiErrorCode.UNAVAILABLE);
        return advice;
    }

    private record OutboundGrades(String courseName, BigDecimal credits, BigDecimal attendanceScore,
                                   BigDecimal assignmentScore, BigDecimal midtermScore, BigDecimal finalScore) {
    }
}
