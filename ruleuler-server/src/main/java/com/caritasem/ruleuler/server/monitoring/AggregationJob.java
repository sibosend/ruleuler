package com.caritasem.ruleuler.server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日聚合任务：凌晨 02:00 从 ClickHouse 聚合前一天变量数据，写入 MySQL Daily_Stats。
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class AggregationJob {

    private static final Logger log = LoggerFactory.getLogger(AggregationJob.class);

    /** 数值型变量类型 */
    static final List<String> NUMERIC_TYPES = List.of(
            "Integer", "Double", "Float", "Long", "BigDecimal", "Boolean");

    /** 类别型变量类型 */
    static final List<String> CATEGORICAL_TYPES = List.of(
            "String", "Char", "Enum");

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    @Autowired
    private JdbcTemplate mysqlJdbc;

    @Autowired
    private AlertChecker alertChecker;

    @Scheduled(cron = "0 0 2 * * ?")
    public void aggregate() {
        aggregateDate(LocalDate.now().minusDays(1));
    }

    public void aggregateDate(LocalDate date) {
        log.info("开始聚合 {} 的变量数据", date);
        try {
            List<DailyStatRow> rows = new ArrayList<>();
            rows.addAll(aggregateNumeric(date));
            rows.addAll(aggregateCategorical(date));
            for (DailyStatRow row : rows) {
                row.alertFlags = alertChecker.check(row);
            }
            if (!rows.isEmpty()) {
                writeToMySQL(rows);
            }
            cleanOldStats();
            log.info("聚合完成，共写入 {} 条记录", rows.size());
        } catch (Exception e) {
            log.error("聚合任务执行失败", e);
            throw new RuntimeException("聚合失败: " + date, e);
        }
    }

    /**
     * 聚合数值型变量：从 ClickHouse 查询基本统计量，再逐变量查询 IQR 异常值数。
     */
    List<DailyStatRow> aggregateNumeric(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        // 第一步：查询基本统计量
        String sql = """
                SELECT project, package_id, var_category, var_name, var_type, io_type,
                    count()                                        AS sample_count,
                    countIf(val_num IS NULL AND val_str IS NULL)    AS missing_count,
                    avg(val_num)                                    AS mean_val,
                    stddevPop(val_num)                              AS std_val,
                    min(val_num)                                    AS min_val,
                    quantile(0.25)(val_num)                         AS p25_val,
                    quantile(0.5)(val_num)                          AS p50_val,
                    quantile(0.75)(val_num)                         AS p75_val,
                    max(val_num)                                    AS max_val,
                    skewPop(val_num)                                AS skewness_val
                FROM execution_var_log FINAL
                WHERE toDate(created_at) = ?
                  AND var_name != ''
                  AND var_type IN (?, ?, ?, ?, ?, ?)
                GROUP BY project, package_id, var_category, var_name, var_type, io_type
                """;

        List<DailyStatRow> rows = new ArrayList<>();
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dateStr);
            for (int i = 0; i < NUMERIC_TYPES.size(); i++) {
                ps.setString(i + 2, NUMERIC_TYPES.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DailyStatRow row = new DailyStatRow();
                    row.project = rs.getString("project");
                    row.packageId = rs.getString("package_id");
                    row.varCategory = rs.getString("var_category");
                    row.varName = rs.getString("var_name");
                    row.varType = rs.getString("var_type");
                    row.ioType = rs.getString("io_type");
                    row.statDate = date;
                    row.sampleCount = rs.getInt("sample_count");
                    int missingCount = rs.getInt("missing_count");
                    row.missingRate = row.sampleCount > 0
                            ? (double) missingCount / row.sampleCount : null;
                    row.mean = getDoubleOrNull(rs, "mean_val");
                    row.std = getDoubleOrNull(rs, "std_val");
                    row.minVal = getDoubleOrNull(rs, "min_val");
                    row.p25 = getDoubleOrNull(rs, "p25_val");
                    row.p50 = getDoubleOrNull(rs, "p50_val");
                    row.p75 = getDoubleOrNull(rs, "p75_val");
                    row.maxVal = getDoubleOrNull(rs, "max_val");
                    row.skewness = getDoubleOrNull(rs, "skewness_val");
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("查询 ClickHouse 数值型聚合失败", e);
            return List.of();
        }

        // 第二步：逐变量查询 IQR 异常值数
        fillOutlierCounts(rows, dateStr);
        return rows;
    }

    /**
     * 用 Q1/Q3 计算 IQR 边界，查询 ClickHouse 获取异常值数。
     */
    private void fillOutlierCounts(List<DailyStatRow> rows, String dateStr) {
        String outlierSql = """
                SELECT count() AS outlier_count
                FROM execution_var_log FINAL
                WHERE toDate(created_at) = ?
                  AND project = ? AND package_id = ? AND var_category = ? AND var_name = ? AND io_type = ?
                  AND val_num IS NOT NULL
                  AND (val_num < ? OR val_num > ?)
                """;
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(outlierSql)) {
            for (DailyStatRow row : rows) {
                if (row.p25 == null || row.p75 == null) {
                    continue;
                }
                double iqr = row.p75 - row.p25;
                double lowerBound = row.p25 - 1.5 * iqr;
                double upperBound = row.p75 + 1.5 * iqr;

                ps.setString(1, dateStr);
                ps.setString(2, row.project);
                ps.setString(3, row.packageId);
                ps.setString(4, row.varCategory);
                ps.setString(5, row.varName);
                ps.setString(6, row.ioType);
                ps.setDouble(7, lowerBound);
                ps.setDouble(8, upperBound);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        row.outlierCount = rs.getInt("outlier_count");
                        // 非 missing 的数值样本数 = sampleCount - missingCount
                        int numericCount = row.sampleCount -
                                (row.missingRate != null ? (int) Math.round(row.missingRate * row.sampleCount) : 0);
                        row.outlierRate = numericCount > 0
                                ? (double) row.outlierCount / numericCount : null;
                    }
                }
                ps.clearParameters();
            }
        } catch (SQLException e) {
            log.error("查询 ClickHouse 异常值数失败", e);
        }
    }

    /**
     * 聚合类别型变量：distinct_count、top_value、top_freq_ratio。
     */
    List<DailyStatRow> aggregateCategorical(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        // topK(1) 返回出现频率最高的值
        String sql = """
                SELECT project, package_id, var_category, var_name, var_type, io_type,
                    count()                                        AS sample_count,
                    countIf(val_num IS NULL AND val_str IS NULL)    AS missing_count,
                    uniqExact(val_str)                              AS distinct_count,
                    topK(1)(val_str)                                AS top_values
                FROM execution_var_log FINAL
                WHERE toDate(created_at) = ?
                  AND var_name != ''
                  AND var_type IN (?, ?, ?)
                GROUP BY project, package_id, var_category, var_name, var_type, io_type
                """;

        List<DailyStatRow> rows = new ArrayList<>();
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dateStr);
            for (int i = 0; i < CATEGORICAL_TYPES.size(); i++) {
                ps.setString(i + 2, CATEGORICAL_TYPES.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DailyStatRow row = new DailyStatRow();
                    row.project = rs.getString("project");
                    row.packageId = rs.getString("package_id");
                    row.varCategory = rs.getString("var_category");
                    row.varName = rs.getString("var_name");
                    row.varType = rs.getString("var_type");
                    row.ioType = rs.getString("io_type");
                    row.statDate = date;
                    row.sampleCount = rs.getInt("sample_count");
                    int missingCount = rs.getInt("missing_count");
                    row.missingRate = row.sampleCount > 0
                            ? (double) missingCount / row.sampleCount : null;
                    row.distinctCount = rs.getInt("distinct_count");
                    // topK(1) 返回 Array(String)，取第一个元素
                    String topValuesRaw = rs.getString("top_values");
                    row.topValue = parseTopKValue(topValuesRaw);
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("查询 ClickHouse 类别型聚合失败", e);
            return List.of();
        }

        // 查询 top_value 的频率
        fillTopFreqRatio(rows, dateStr);
        return rows;
    }

    /**
     * 查询 top_value 在该变量中的出现频率。
     */
    private void fillTopFreqRatio(List<DailyStatRow> rows, String dateStr) {
        String freqSql = """
                SELECT count() AS freq
                FROM execution_var_log FINAL
                WHERE toDate(created_at) = ?
                  AND project = ? AND package_id = ? AND var_category = ? AND var_name = ? AND io_type = ?
                  AND val_str = ?
                """;
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(freqSql)) {
            for (DailyStatRow row : rows) {
                if (row.topValue == null || row.sampleCount == 0) {
                    continue;
                }
                ps.setString(1, dateStr);
                ps.setString(2, row.project);
                ps.setString(3, row.packageId);
                ps.setString(4, row.varCategory);
                ps.setString(5, row.varName);
                ps.setString(6, row.ioType);
                ps.setString(7, row.topValue);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int freq = rs.getInt("freq");
                        row.topFreqRatio = (double) freq / row.sampleCount;
                    }
                }
                ps.clearParameters();
            }
        } catch (SQLException e) {
            log.error("查询 ClickHouse top_value 频率失败", e);
        }
    }

    /**
     * REPLACE INTO MySQL Daily_Stats 表。
     */
    void writeToMySQL(List<DailyStatRow> rows) {
        String sql = """
                REPLACE INTO ruleuler_variable_daily_stats
                (project, package_id, var_category, var_name, var_type, io_type, stat_date,
                 sample_count, missing_rate,
                 mean, std, min_val, p25, p50, p75, max_val, skewness, outlier_count, outlier_rate,
                 distinct_count, top_value, top_freq_ratio, alert_flags)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        mysqlJdbc.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setString(1, row.project);
            ps.setString(2, row.packageId);
            ps.setString(3, row.varCategory);
            ps.setString(4, row.varName);
            ps.setString(5, row.varType);
            ps.setString(6, row.ioType);
            ps.setDate(7, Date.valueOf(row.statDate));
            ps.setInt(8, row.sampleCount);
            setDoubleOrNull(ps, 9, row.missingRate);
            setDoubleOrNull(ps, 10, row.mean);
            setDoubleOrNull(ps, 11, row.std);
            setDoubleOrNull(ps, 12, row.minVal);
            setDoubleOrNull(ps, 13, row.p25);
            setDoubleOrNull(ps, 14, row.p50);
            setDoubleOrNull(ps, 15, row.p75);
            setDoubleOrNull(ps, 16, row.maxVal);
            setDoubleOrNull(ps, 17, row.skewness);
            setIntOrNull(ps, 18, row.outlierCount);
            setDoubleOrNull(ps, 19, row.outlierRate);
            setIntOrNull(ps, 20, row.distinctCount);
            ps.setString(21, row.topValue);
            setDoubleOrNull(ps, 22, row.topFreqRatio);
            ps.setString(23, row.alertFlags);
        });
    }

    /**
     * 删除超过 3 个月的历史记录。
     */
    void cleanOldStats() {
        LocalDate cutoff = LocalDate.now().minusMonths(3);
        int deleted = mysqlJdbc.update(
                "DELETE FROM ruleuler_variable_daily_stats WHERE stat_date < ?",
                Date.valueOf(cutoff));
        if (deleted > 0) {
            log.info("清理 {} 条超过 3 个月的历史统计记录", deleted);
        }
    }

    // ---- 工具方法 ----

    /**
     * 解析 ClickHouse topK(1) 返回的 Array 字符串，提取第一个值。
     * topK 返回格式如 ['value'] 或 JDBC 直接返回数组。
     */
    static String parseTopKValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        // JDBC 可能返回 ['val'] 格式
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Double getDoubleOrNull(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }

    private static void setDoubleOrNull(PreparedStatement ps, int idx, Double val) throws SQLException {
        if (val == null) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, val);
        }
    }

    private static void setIntOrNull(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, val);
        }
    }

    /**
     * 聚合结果行，内部使用。
     */
    static class DailyStatRow {
        String project;
        String packageId;
        String varCategory;
        String varName;
        String varType;
        String ioType;
        LocalDate statDate;
        int sampleCount;
        Double missingRate;
        // 数值型
        Double mean;
        Double std;
        Double minVal;
        Double p25;
        Double p50;
        Double p75;
        Double maxVal;
        Double skewness;
        Integer outlierCount;
        Double outlierRate;
        // 类别型
        Integer distinctCount;
        String topValue;
        Double topFreqRatio;
        // 告警
        String alertFlags;
    }
}
