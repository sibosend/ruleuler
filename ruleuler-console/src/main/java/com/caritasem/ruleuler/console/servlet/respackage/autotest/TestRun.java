package com.caritasem.ruleuler.console.servlet.respackage.autotest;

public class TestRun {
    private long id;
    private String project;
    private String packageId;
    private long packId;
    private String runType;        // "smoke" / "regression"
    private Long baselineRunId;    // 回归基准 run，null 表示本次是 baseline
    private int executedCases;     // 已执行用例数（进度）
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private String status;
    private long startedAt;
    private Long finishedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public long getPackId() {
        return packId;
    }

    public void setPackId(long packId) {
        this.packId = packId;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public Long getBaselineRunId() {
        return baselineRunId;
    }

    public void setBaselineRunId(Long baselineRunId) {
        this.baselineRunId = baselineRunId;
    }

    public int getExecutedCases() {
        return executedCases;
    }

    public void setExecutedCases(int executedCases) {
        this.executedCases = executedCases;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }

    public int getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(int passedCases) {
        this.passedCases = passedCases;
    }

    public int getFailedCases() {
        return failedCases;
    }

    public void setFailedCases(int failedCases) {
        this.failedCases = failedCases;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Long finishedAt) {
        this.finishedAt = finishedAt;
    }
}
