package com.caritasem.ruleuler.server.auth;

import io.jsonwebtoken.Claims;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: modern-admin-console, Property 13: JWT round trip
class JwtUtilPropertyTest {

    private static final String SECRET = "test-secret-key-must-be-32bytes!";
    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 24);

    // **Validates: Requirements 11.2**
    @Property(tries = 100)
    void jwtRoundTrip(
            @ForAll @IntRange(min = 1, max = 100000) int userIdInt,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String username,
            @ForAll @Size(min = 1, max = 5) List<@AlphaChars @StringLength(min = 1, max = 10) String> roles
    ) {
        Long userId = (long) userIdInt;

        String token = jwtUtil.generateToken(userId, username, roles);
        Claims claims = jwtUtil.parseToken(token);

        assertThat(jwtUtil.getUserId(claims)).isEqualTo(userId);
        assertThat(jwtUtil.getUsername(claims)).isEqualTo(username);
        assertThat(jwtUtil.getRoles(claims)).isEqualTo(roles);
    }
}
