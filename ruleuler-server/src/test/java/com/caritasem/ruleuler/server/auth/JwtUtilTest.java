package com.caritasem.ruleuler.server.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    // 32 bytes secret for HS256
    private static final String SECRET = "test-secret-key-must-be-32bytes!";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 24);
    }

    @Test
    void generateAndParse_roundTrip() {
        String token = jwtUtil.generateToken(1L, "admin", List.of("admin", "editor"));
        Claims claims = jwtUtil.parseToken(token);

        assertEquals("admin", jwtUtil.getUsername(claims));
        assertEquals(1L, jwtUtil.getUserId(claims));
        assertEquals(List.of("admin", "editor"), jwtUtil.getRoles(claims));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void parseToken_invalidToken_throws() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("invalid.token.here"));
    }

    @Test
    void parseToken_tamperedToken_throws() {
        String token = jwtUtil.generateToken(1L, "admin", List.of("admin"));
        // tamper with the token
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered));
    }

    @Test
    void parseToken_expiredToken_throws() {
        // expiration = 0 hours → already expired
        JwtUtil expiredUtil = new JwtUtil(SECRET, 0);
        String token = expiredUtil.generateToken(1L, "admin", List.of("admin"));
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    void parseToken_wrongKey_throws() {
        JwtUtil otherUtil = new JwtUtil("another-secret-key-32bytes-long!", 24);
        String token = otherUtil.generateToken(1L, "admin", List.of("admin"));
        assertThrows(Exception.class, () -> jwtUtil.parseToken(token));
    }
}
