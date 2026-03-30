package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;

/**
 * 输入变量的一个值区间，由规则条件中的阈值切分产生。
 */
public class Segment {
    private String variableName;
    private String variableCategory;
    private Datatype datatype;
    private Op lowerOp;        // null = 无下界
    private String lowerValue;
    private Op upperOp;        // null = 无上界
    private String upperValue;
    private Object representative; // 代表值
    private String label;          // 可读描述，如 "[7,9]"

    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }

    public String getVariableCategory() { return variableCategory; }
    public void setVariableCategory(String variableCategory) { this.variableCategory = variableCategory; }

    public Datatype getDatatype() { return datatype; }
    public void setDatatype(Datatype datatype) { this.datatype = datatype; }

    public Op getLowerOp() { return lowerOp; }
    public void setLowerOp(Op lowerOp) { this.lowerOp = lowerOp; }

    public String getLowerValue() { return lowerValue; }
    public void setLowerValue(String lowerValue) { this.lowerValue = lowerValue; }

    public Op getUpperOp() { return upperOp; }
    public void setUpperOp(Op upperOp) { this.upperOp = upperOp; }

    public String getUpperValue() { return upperValue; }
    public void setUpperValue(String upperValue) { this.upperValue = upperValue; }

    public Object getRepresentative() { return representative; }
    public void setRepresentative(Object representative) { this.representative = representative; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
