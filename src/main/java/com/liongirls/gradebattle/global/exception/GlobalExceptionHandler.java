package com.liongirls.gradebattle.global.exception;

import com.liongirls.gradebattle.global.code.BaseErrorCode;
import com.liongirls.gradebattle.global.code.GlobalErrorCode;
import com.liongirls.gradebattle.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 직접 던진 GeneralException 처리
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(GeneralException e) {
        BaseErrorCode errorCode = e.getErrorCode();

        log.warn("GeneralException occurred. code: {}, message: {}",
                errorCode.getCode(),
                errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage(), null));
    }

    //@Valid 유효성 검사 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {

        // 에러 메시지 가져오기
        String errorMessage = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("잘못된 요청입니다.");

        log.warn("Validation failed: {}", errorMessage);

        GlobalErrorCode errorCode = GlobalErrorCode.BAD_REQUEST;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorMessage, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessageException(
            HttpMessageNotReadableException e
    ) {
        GlobalErrorCode errorCode = GlobalErrorCode.BAD_REQUEST;

        log.warn("Unreadable request body");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        null
                ));
    }

    // PathVariable, RequestParam 등의 타입 변환 실패 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        GlobalErrorCode errorCode = GlobalErrorCode.BAD_REQUEST;

        log.warn(
                "Type mismatch. parameter: {}, requiredType: {}",
                e.getName(),
                e.getRequiredType()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        null
                ));
    }

    // 존재하지 않는 URL 또는 정적 리소스 요청 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("No resource found: {}", e.getResourcePath());

        GlobalErrorCode errorCode = GlobalErrorCode.NOT_FOUND;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage(), null));
    }

    // 지원하지 않는 HTTP 메서드 호출 처리
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowedException(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMethod());

        GlobalErrorCode errorCode = GlobalErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage(), null));
    }

    // 그 외 에러 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception e) {
        log.error("Unhandled exception type: {}", e.getClass().getSimpleName());

        GlobalErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage(), null));
    }

}
