package com.caritasem.ruleuler.server.monitoring;

/**
 * 环比百分比计算工具类。
 * Feature: monitoring-realtime-enhancement, Property 6: 环比百分比计算
 */
public class DiffPctCalculator {

    /**
     * 计算环比百分比：(b - a) / |a| * 100
     * 
     * @param a 基准值（昨日/上周）
     * @param b 当前值（今日）
     * @return 变化百分比，当 a=0 或任一值为 null 时返回 null
     */
    public static Double computeDiffPct(Double a, Double b) {
        if (a == null || b == null) {
            return null;
        }
        if (a == 0.0) {
            return null;
        }
        return ((b - a) / Math.abs(a)) * 100;
    }
}