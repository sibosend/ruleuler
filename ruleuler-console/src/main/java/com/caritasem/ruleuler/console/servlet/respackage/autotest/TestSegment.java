package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import java.math.BigDecimal;

/**
 * 对应 ruleuler_test_segment 表，存储分段统计数据。
 */
public class TestSegment {
    private long id;
    private long runId;
    private String variableName;
    private String variableType;   // INPUT / OUTPUT
    private String segmentLabel;
    private int caseCount;
    private BigDecimal percentage;
    private Integer baselineCount;
    private BigDecimal baselinePercentage;
    private BigDecimal changePct;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getVariableType() { return variableType; }
    public void setVariableType(String variableType) { this.variableType = variableType; }

    public String getSegmentLabel() { return segmentLabel; }
    public void setSegmentLabel(String segmentLabel) { this.segmentLabel = segmentLabel; }

    public int getCaseCount() { return caseCount; }
    public void setCaseCount(int caseCount) { this.caseCount = caseCount; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

    public Integer getBaselineCount() { return baselineCount; }
    public void setBaselineCount(Integer baselineCount) { this.baselineCount = baselineCount; }

    public BigDecimal getBaselinePercentage() { return baselinePercentage; }
    public void setBaselinePercentage(BigDecimal baselinePercentage) { this.baselinePercentage = baselinePercentage; }

    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
}
