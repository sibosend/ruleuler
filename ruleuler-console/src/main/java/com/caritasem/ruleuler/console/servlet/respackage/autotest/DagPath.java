package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import java.util.List;

public class DagPath {
    private List<String> nodeNames;
    private List<ConditionConstraint> constraints;
    private String description;

    public List<String> getNodeNames() {
        return nodeNames;
    }

    public void setNodeNames(List<String> nodeNames) {
        this.nodeNames = nodeNames;
    }

    public List<ConditionConstraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<ConditionConstraint> constraints) {
        this.constraints = constraints;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
