package com.caritasem.ruleuler.server.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private Integer status;
    private Integer builtIn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
