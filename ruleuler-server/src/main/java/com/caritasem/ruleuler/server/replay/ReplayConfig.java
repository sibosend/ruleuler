package com.caritasem.ruleuler.server.replay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

@Data
@ConfigurationProperties(prefix = "replay")
public class ReplayConfig {

    private int maxConcurrentSessions;
    private long sessionTimeoutMs;
    private int maxSampleSize;

    @PostConstruct
    void validate() {
        if (maxConcurrentSessions <= 0) {
            throw new IllegalStateException("配置项 replay.max-concurrent-sessions 缺失或无效");
        }
        if (sessionTimeoutMs <= 0) {
            throw new IllegalStateException("配置项 replay.session-timeout-ms 缺失或无效");
        }
        if (maxSampleSize <= 0) {
            throw new IllegalStateException("配置项 replay.max-sample-size 缺失或无效");
        }
    }
}
