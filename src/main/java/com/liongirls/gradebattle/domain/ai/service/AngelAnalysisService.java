package com.liongirls.gradebattle.domain.ai.service;

import com.liongirls.gradebattle.domain.ai.client.OpenAiAngelClient;
import com.liongirls.gradebattle.domain.ai.dto.*;
import com.liongirls.gradebattle.domain.ai.exception.AiErrorCode;
import com.liongirls.gradebattle.global.exception.GeneralException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AngelAnalysisService {
    public static final String NOTICE_VERSION = "angel-v1";
    public static final String NOTICE = "실제 과목명은 '분석 과목'으로 익명화하고, 성적과 이수학점만 OpenAI 서버(api.openai.com)에 전송합니다. "
            + "이름·학번·이메일·사용자 ID·인증 정보는 전송하지 않습니다. 분석 결과는 DB에 저장하지 않습니다. "
            + "이번 분석의 외부 전송에 동의할 때만 분석 버튼을 눌러 주세요.";
    private static final String SESSION_KEY = AngelAnalysisService.class.getName() + ".grades";

    private final ScoreCalculator scoreCalculator;
    private final OpenAiAngelClient openAiAngelClient;

    public SavedGrades saveGrades(Long authenticatedUserId, AngelAnalysisRequest request, HttpSession session) {
        SavedGrades saved = new SavedGrades(UUID.randomUUID().toString(), request);
        session.setAttribute(SESSION_KEY, new OwnedGrades(authenticatedUserId, saved));
        return saved;
    }

    public SavedGrades getGrades(Long authenticatedUserId, HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (!(value instanceof OwnedGrades owned)) throw new GeneralException(AiErrorCode.GRADES_REQUIRED);
        if (!owned.ownerId().equals(authenticatedUserId)) throw new GeneralException(AiErrorCode.FORBIDDEN);
        return owned.saved();
    }

    public AngelAnalysisResponse analyze(Long authenticatedUserId, AnalysisConsentRequest consent, HttpSession session) {
        if (!Boolean.TRUE.equals(consent.consent()) || !NOTICE_VERSION.equals(consent.noticeVersion())) {
            throw new GeneralException(AiErrorCode.CONSENT_REQUIRED);
        }
        SavedGrades saved = getGrades(authenticatedUserId, session);
        if (!saved.gradeVersion().equals(consent.gradeVersion())) throw new GeneralException(AiErrorCode.GRADES_REQUIRED);
        var summary = scoreCalculator.calculate(saved.grades());
        return new AngelAnalysisResponse(summary, openAiAngelClient.analyze(saved.grades()));
    }

    // 성적은 현재 로그인 사용자의 세션에만 보관한다. 로그아웃/세션 만료 시 제거된다.
    public record SavedGrades(String gradeVersion, AngelAnalysisRequest grades) {
    }

    private record OwnedGrades(Long ownerId, SavedGrades saved) {
    }
}
