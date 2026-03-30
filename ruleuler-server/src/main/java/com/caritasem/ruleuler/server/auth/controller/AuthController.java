package com.caritasem.ruleuler.server.auth.controller;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        AuthService.LoginResult result = authService.login(username, password);
        return ApiResult.ok(Map.of(
                "token", result.token(),
                "user", Map.of(
                        "id", result.userId(),
                        "username", result.username(),
                        "roles", result.roles(),
                        "permissions", result.permissions()
                )
        ));
    }

    @GetMapping("/me")
    public ApiResult me() {
        AuthContext.UserInfo user = AuthContext.get();
        if (user == null) {
            throw new IllegalArgumentException("未登录");
        }
        AuthService.UserInfoResult result = authService.getCurrentUser(user.getUserId());
        return ApiResult.ok(Map.of(
                "id", result.userId(),
                "username", result.username(),
                "roles", result.roles(),
                "permissions", result.permissions()
        ));
    }
}
