package com.caritasem.ruleuler.server.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警检查器：检查统计指标是否超过配置阈值，标记 alert_flags。
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class AlertChecker {

    private final AlertConfigService configService;

    public AlertChecker(AlertConfigService configService) {
        this.configService = configService;
    }

    /**
     * 检查统计指标，返回告警标记（逗号分隔的告警类型）。
     * 返回 null 表示无告警。
     */
    public String check(AggregationJob.DailyStatRow row) {
        AlertConfig config = configService.getCachedConfig();
        return check(row.missingRate, row.outlierRate, row.skewness,
                config.getMissingRateMax(), config.getOutlierRateMax(), config.getSkewnessAbsMax());
    }

    /**
     * 纯函数版本，用于属性测试。
     */
    public static String check(Double missingRate, Double outlierRate, Double skewness,
                               double missingRateMax, double outlierRateMax, double skewnessAbsMax) {
        List<String> flags = new ArrayList<>();
        if (missingRate != null && missingRate > missingRateMax) {
            flags.add("missing_rate");
        }
        if (outlierRate != null && outlierRate > outlierRateMax) {
            flags.add("outlier_rate");
        }
        if (skewness != null && Math.abs(skewness) > skewnessAbsMax) {
            flags.add("skewness");
        }
        return flags.isEmpty() ? null : String.join(",", flags);
    }
}
