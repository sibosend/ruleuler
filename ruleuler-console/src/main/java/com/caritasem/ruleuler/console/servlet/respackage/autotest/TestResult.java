package com.caritasem.ruleuler.console.servlet.respackage.autotest;

public class TestResult {
    private long id;
    private long runId;
    private long caseId;
    private boolean passed;
    private String actualOutput;
    private Long executionTimeMs;
    private String errorMessage;
    private String baselineOutput;  // baseline run 中同一 case_id 的 actual_output
    private String diffStatus;      // "BASELINE" / "SAME" / "CHANGED"
    private long createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public long getCaseId() {
        return caseId;
    }

    public void setCaseId(long caseId) {
        this.caseId = caseId;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getBaselineOutput() {
        return baselineOutput;
    }

    public void setBaselineOutput(String baselineOutput) {
        this.baselineOutput = baselineOutput;
    }

    public String getDiffStatus() {
        return diffStatus;
    }

    public void setDiffStatus(String diffStatus) {
        this.diffStatus = diffStatus;
    }
}
