package com.caritasem.ruleuler.server.replay.model;

public record ToleranceConfig(
    String mode,
    double value
) {
    public static ToleranceConfig exact() {
        return new ToleranceConfig("exact", 0);
    }
}
