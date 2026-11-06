package com.liongirls.gradebattle.domain.ai.controller;

import com.liongirls.gradebattle.domain.ai.dto.*;
import com.liongirls.gradebattle.domain.ai.service.AngelAnalysisService;
import com.liongirls.gradebattle.global.response.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AngelAnalysisController {
    private final AngelAnalysisService service;

    @GetMapping("/consent")
    public ApiResponse<ConsentNotice> consent() {
        return ApiResponse.success(new ConsentNotice(AngelAnalysisService.NOTICE_VERSION, AngelAnalysisService.NOTICE));
    }

    @PutMapping("/grades/me")
    public ApiResponse<AngelAnalysisService.SavedGrades> saveGrades(Authentication authentication, HttpSession session,
                                                                  @Valid @RequestBody AngelAnalysisRequest request) {
        return ApiResponse.success(service.saveGrades(Long.valueOf(authentication.getName()), request, session));
    }

    @GetMapping("/grades/me")
    public ApiResponse<AngelAnalysisService.SavedGrades> getGrades(Authentication authentication, HttpSession session) {
        return ApiResponse.success(service.getGrades(Long.valueOf(authentication.getName()), session));
    }

    @PostMapping("/analyze")
    public ApiResponse<AngelAnalysisResponse> analyze(Authentication authentication, HttpSession session,
                                                     @Valid @RequestBody AnalysisConsentRequest request) {
        return ApiResponse.success(service.analyze(Long.valueOf(authentication.getName()), request, session));
    }

    public record ConsentNotice(String noticeVersion, String notice) {
    }
}
