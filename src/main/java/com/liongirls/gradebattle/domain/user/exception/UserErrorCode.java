package com.liongirls.gradebattle.domain.user.exception;

import com.liongirls.gradebattle.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "USER_409", "이미 사용 중인 아이디입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "USER_401", "아이디 또는 비밀번호가 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER_400", "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
