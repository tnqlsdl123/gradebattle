package com.liongirls.gradebattle.domain.user.controller;

import com.liongirls.gradebattle.domain.user.dto.LoginRequest;
import com.liongirls.gradebattle.domain.user.dto.SignupRequest;
import com.liongirls.gradebattle.domain.user.dto.UpdateNicknameRequest;
import com.liongirls.gradebattle.domain.user.dto.UserResponse;
import com.liongirls.gradebattle.global.exception.GeneralException;
import com.liongirls.gradebattle.global.response.ApiResponse;
import com.liongirls.gradebattle.domain.user.exception.UserErrorCode;
import com.liongirls.gradebattle.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
        return ApiResponse.success(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(userService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest body,
                                           HttpServletRequest request, HttpServletResponse response) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new GeneralException(UserErrorCode.INVALID_CREDENTIALS);
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
        } catch (AuthenticationException e) {
            throw new GeneralException(UserErrorCode.INVALID_CREDENTIALS);
        }
        UserResponse user = userService.getMe(Long.valueOf(authentication.getName()));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ApiResponse.success(user);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success(userService.getMe(Long.valueOf(authentication.getName())));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateNickname(Authentication authentication,
                                                     @Valid @RequestBody UpdateNicknameRequest request) {
        return ApiResponse.success(userService.updateNickname(Long.valueOf(authentication.getName()), request.nickname()));
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
