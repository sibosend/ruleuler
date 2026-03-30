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
public class Role {
    private Long id;
    private String name;
    private String description;
    private Integer builtIn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
