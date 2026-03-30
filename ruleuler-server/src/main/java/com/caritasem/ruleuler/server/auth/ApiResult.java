package com.caritasem.ruleuler.server.auth;

/**
 * 统一 API 响应格式。
 */
public record ApiResult(int code, String message, Object data) {

    public static ApiResult ok(Object data) {
        return new ApiResult(200, "success", data);
    }

    public static ApiResult error(int code, String message) {
        return new ApiResult(code, message, null);
    }
}
