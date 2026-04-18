package com.caritasem.ruleuler.dto;

import java.util.Map;

public record RuleExecutionResult(
    int status,
    String msg,
    Map<String, Object> data,
    Map<String, Object> meta
) {
    public boolean isSuccess() {
        return status == 200;
    }
}
