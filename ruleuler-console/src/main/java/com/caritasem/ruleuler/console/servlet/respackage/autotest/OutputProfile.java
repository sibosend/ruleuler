package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;

import java.util.List;
import java.util.Set;

/**
 * 输出变量的可能取值画像。
 */
public class OutputProfile {
    private String variableName;
    private String variableCategory; // "GateResult" / "parameter"
    private Datatype datatype;
    private Set<String> possibleValues;
    private List<String> sourceRules;
    private boolean overridable;     // 是否被多个节点赋值

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getVariableCategory() { return variableCategory; }
    public void setVariableCategory(String variableCategory) { this.variableCategory = variableCategory; }

    public Datatype getDatatype() { return datatype; }
    public void setDatatype(Datatype datatype) { this.datatype = datatype; }

    public Set<String> getPossibleValues() { return possibleValues; }
    public void setPossibleValues(Set<String> possibleValues) { this.possibleValues = possibleValues; }

    public List<String> getSourceRules() { return sourceRules; }
    public void setSourceRules(List<String> sourceRules) { this.sourceRules = sourceRules; }

    public boolean isOverridable() { return overridable; }
    public void setOverridable(boolean overridable) { this.overridable = overridable; }
}
