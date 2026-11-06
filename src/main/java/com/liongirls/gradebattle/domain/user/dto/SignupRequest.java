package com.liongirls.gradebattle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]{4,50}", message = "아이디는 영문, 숫자, 밑줄로 4~50자여야 합니다.")
        String loginId,
        @NotBlank
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상, 72바이트 이하여야 합니다.")
        String password,
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname
) {
}
