package com.liongirls.gradebattle.global.response;

public record ApiResponse<T>(
        boolean isSuccess,
        String code,
        String message,
        T result
) {
    // 데이터가 있는 성공 응답
    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(true, "COMMON_200", "요청에 성공했습니다.", result);
    }
    //데이터가 없는 성공 응답 (오버로딩)
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "COMMON_200", "요청에 성공했습니다.", null);
    }
    // 실패 시 호출
    public static <T> ApiResponse<T> failure(String code, String message, T result) {
        return new ApiResponse<>(false, code, message, result);
    }
}
