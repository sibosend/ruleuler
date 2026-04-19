package com.bstek.urule.runtime.shadow;

import java.util.Map;

/**
 * 影子规则命中信息，记录单次 shadow 规则执行的快照。
 */
public class ShadowHitInfo {
    private final String ruleName;
    private final Map<String, Object> inputSnapshot;
    private final Map<String, Object> outputSnapshot;
    private final long execMs;
    private final String errorMsg;

    public ShadowHitInfo(String ruleName, Map<String, Object> inputSnapshot,
                         Map<String, Object> outputSnapshot, long execMs, String errorMsg) {
        this.ruleName = ruleName;
        this.inputSnapshot = inputSnapshot;
        this.outputSnapshot = outputSnapshot;
        this.execMs = execMs;
        this.errorMsg = errorMsg;
    }

    public String getRuleName() { return ruleName; }
    public Map<String, Object> getInputSnapshot() { return inputSnapshot; }
    public Map<String, Object> getOutputSnapshot() { return outputSnapshot; }
    public long getExecMs() { return execMs; }
    public String getErrorMsg() { return errorMsg; }
}
