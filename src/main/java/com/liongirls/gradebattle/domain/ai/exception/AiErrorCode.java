package com.liongirls.gradebattle.domain.ai.exception;

import com.liongirls.gradebattle.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiErrorCode implements BaseErrorCode {
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "AI_400_CONSENT", "안내를 확인하고 이번 분석의 외부 전송에 동의해 주세요."),
    GRADES_REQUIRED(HttpStatus.BAD_REQUEST, "AI_400_GRADES", "본인의 성적을 입력하고 다시 동의해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AI_403", "본인의 성적만 분석할 수 있습니다."),
    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI_503_CONFIG", "AI API 키 또는 모델 설정을 확인해 주세요."),
    RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "AI_503_LIMIT", "AI 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_504", "AI 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."),
    UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI_502", "AI 분석을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    REFUSED(HttpStatus.UNPROCESSABLE_CONTENT, "AI_422", "입력한 점수로는 분석할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
