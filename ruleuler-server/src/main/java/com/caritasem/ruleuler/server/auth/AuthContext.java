package com.caritasem.ruleuler.server.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ThreadLocal 存储当前请求用户信息。
 * Filter 中 set，Controller 中 get，请求结束 clear。
 */
public final class AuthContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(UserInfo userInfo) {
        HOLDER.set(userInfo);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private List<String> roles;
        private List<String> permissions;
    }
}
