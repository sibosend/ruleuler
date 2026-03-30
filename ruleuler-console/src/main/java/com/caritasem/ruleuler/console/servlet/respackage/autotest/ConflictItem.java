package com.caritasem.ruleuler.console.servlet.respackage.autotest;

public class ConflictItem {
    private String conflictType;   // OVERLAP / INCOMPLETE / OVERRIDE
    private String severity;       // ERROR / WARNING
    private String ruleFile;       // 涉及的规则文件路径
    private String location;       // 具体位置描述
    private String description;    // 冲突描述
    private long runId;            // 关联的 test_run

    public String getConflictType() {
        return conflictType;
    }

    public void setConflictType(String conflictType) {
        this.conflictType = conflictType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRuleFile() {
        return ruleFile;
    }

    public void setRuleFile(String ruleFile) {
        this.ruleFile = ruleFile;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }
}
