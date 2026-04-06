package com.caritasem.ruleuler.server.monitoring;

import com.caritasem.ruleuler.server.auth.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 监控 REST API，提供变量列表、趋势、周期对比、执行记录查询。
 * RBAC 由 RbacPermissionFilter 根据 api:GET:/api/monitoring/* 自动拦截。
 */
@RestController
@RequestMapping("/api/monitoring")
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);
    private static final long MAX_RANGE_DAYS = 93; // ~3个月

    @Autowired
    private JdbcTemplate mysqlJdbc;

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    @Autowired
    private AlertConfigService alertConfigService;

    /**
     * 变量列表 + 最新指标摘要（MySQL）。
     * 默认只展示最近7天有数据的活跃变量，showAll=true 展示全部。
     */
    @GetMapping("/variables")
    public ApiResult variables(@RequestParam String project,
                               @RequestParam String packageId,
                               @RequestParam(defaultValue = "input") String ioType,
                               @RequestParam(defaultValue = "false") boolean showAll) {
        String sql = """
                SELECT ds.*,
                    DATEDIFF(CURDATE(), ds.stat_date) AS days_since_last
                FROM ruleuler_variable_daily_stats ds
                INNER JOIN (
                    SELECT project, package_id, var_category, var_name, io_type, MAX(stat_date) AS max_date
                    FROM ruleuler_variable_daily_stats
                    WHERE project = ? AND package_id = ? AND io_type = ?
                    GROUP BY project, package_id, var_category, var_name, io_type
                ) latest ON ds.project = latest.project
                    AND ds.package_id = latest.package_id
                    AND ds.var_category = latest.var_category
                    AND ds.var_name = latest.var_name
                    AND ds.io_type = latest.io_type
                    AND ds.stat_date = latest.max_date
                WHERE ds.project = ? AND ds.package_id = ? AND ds.io_type = ?
                """;
        if (!showAll) {
            sql += " HAVING days_since_last <= 7";
        }
        sql += " ORDER BY ds.var_category, ds.var_name";

        List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql,
                project, packageId, ioType, project, packageId, ioType);
        return ApiResult.ok(rows);
    }

    /**
     * 变量趋势数据（MySQL），默认最近30天，最大3个月。
     */
    @GetMapping("/trend")
    public ApiResult trend(@RequestParam String project,
                           @RequestParam String packageId,
                           @RequestParam String varCategory,
                           @RequestParam String varName,
                           @RequestParam(defaultValue = "input") String ioType,
                           @RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate) {
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(30);

        String rangeErr = validateRange(start, end);
        if (rangeErr != null) return ApiResult.error(400, rangeErr);

        String sql = """
                SELECT * FROM ruleuler_variable_daily_stats
                WHERE project = ? AND package_id = ? AND var_category = ? AND var_name = ? AND io_type = ?
                  AND stat_date BETWEEN ? AND ?
                ORDER BY stat_date
                """;
        List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql,
                project, packageId, varCategory, varName, ioType,
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
        return ApiResult.ok(rows);
    }

    /**
     * 周期对比（ClickHouse 直接聚合），对比两个时间段内变量的统计指标。
     */
    @PostMapping("/compare")
    public ApiResult compare(@RequestBody CompareRequest req) {
        if (req.variables == null || req.variables.isEmpty()) {
            return ApiResult.error(400, "变量列表不能为空");
        }
        if (req.periodA == null || req.periodB == null) {
            return ApiResult.error(400, "必须指定两个对比周期");
        }
        String errA = validateRange(req.periodA.start, req.periodA.end);
        if (errA != null) return ApiResult.error(400, "周期A: " + errA);
        String errB = validateRange(req.periodB.start, req.periodB.end);
        if (errB != null) return ApiResult.error(400, "周期B: " + errB);

        List<Map<String, Object>> results = new ArrayList<>();
        for (CompareVariable v : req.variables) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("varCategory", v.varCategory);
            item.put("varName", v.varName);
            item.put("ioType", v.ioType);
            item.put("periodA", aggregateFromClickHouse(req.project, req.packageId,
                    v.varCategory, v.varName, v.ioType, req.periodA.start, req.periodA.end));
            item.put("periodB", aggregateFromClickHouse(req.project, req.packageId,
                    v.varCategory, v.varName, v.ioType, req.periodB.start, req.periodB.end));
            results.add(item);
        }
        return ApiResult.ok(results);
    }

    /**
     * 执行记录分页（ClickHouse），按 execution_id 聚合。
     */
    @GetMapping("/executions")
    public ApiResult executions(@RequestParam String project,
                                @RequestParam String packageId,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int pageSize) {
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(30);

        String rangeErr = validateRange(start, end);
        if (rangeErr != null) return ApiResult.error(400, rangeErr);

        int offset = (page - 1) * pageSize;
        String sql = """
                SELECT execution_id, project, package_id, flow_id,
                    min(created_at) AS exec_time,
                    any(exec_ms) AS exec_ms,
                    count() AS var_count,
                    if(countIf(var_name = '') > 0, 'failed', 'success') AS status
                FROM execution_var_log FINAL
                WHERE toDate(created_at) BETWEEN ? AND ?
                  AND project = ? AND package_id = ?
                GROUP BY execution_id, project, package_id, flow_id
                ORDER BY exec_time DESC
                LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> rows = queryClickHouse(sql,
                start.toString(), end.toString(), project, packageId, pageSize, offset);
        return ApiResult.ok(rows);
    }

    /**
     * 单次执行变量明细（ClickHouse）。
     */
    @GetMapping("/executions/{id}")
    public ApiResult executionDetail(@PathVariable String id) {
        String sql = """
                SELECT * FROM execution_var_log FINAL
                WHERE execution_id = ?
                ORDER BY var_category, var_name
                """;
        List<Map<String, Object>> rows = queryClickHouse(sql, id);
        return ApiResult.ok(rows);
    }

    @GetMapping("/alert-config")
    public ApiResult getAlertConfig() {
        return ApiResult.ok(alertConfigService.getCachedConfig());
    }

    @PutMapping("/alert-config")
    public ApiResult updateAlertConfig(@RequestBody AlertConfig config) {
        try {
            alertConfigService.update(config);
            return ApiResult.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResult.error(400, e.getMessage());
        }
    }

    @Autowired
    private AggregationJob aggregationJob;

    /**
     * 手动触发指定日期的聚合任务。
     * POST /api/monitoring/aggregate?date=2026-04-05
     * 不传 date 则聚合昨天。
     */
    @PostMapping("/aggregate")
    public ApiResult manualAggregate(@RequestParam(required = false) String date) {
        try {
            if (date != null) {
                LocalDate target = LocalDate.parse(date);
                aggregationJob.aggregateDate(target);
            } else {
                aggregationJob.aggregate();
            }
            return ApiResult.ok("聚合完成");
        } catch (Exception e) {
            return ApiResult.error(500, "聚合失败: " + e.getMessage());
        }
    }

    // ---- 内部方法 ----

    /**
     * 校验时间范围不超过3个月。
     */
    static String validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            return "开始日期不能晚于结束日期";
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            return "时间范围不能超过3个月";
        }
        return null;
    }

    /**
     * 从 ClickHouse 聚合指定变量在指定时间段的统计指标（数值型 + 类别型通用）。
     */
    private Map<String, Object> aggregateFromClickHouse(String project, String packageId,
                                                         String varCategory, String varName,
                                                         String ioType,
                                                         LocalDate start, LocalDate end) {
        String sql = """
                SELECT
                    count()                                        AS sample_count,
                    countIf(val_num IS NULL AND val_str IS NULL)    AS missing_count,
                    avg(val_num)                                    AS mean,
                    stddevPop(val_num)                              AS std,
                    min(val_num)                                    AS min_val,
                    quantile(0.25)(val_num)                         AS p25,
                    quantile(0.5)(val_num)                          AS p50,
                    quantile(0.75)(val_num)                         AS p75,
                    max(val_num)                                    AS max_val,
                    uniqExact(val_str)                              AS distinct_count,
                    topK(1)(val_str)                                AS top_values
                FROM execution_var_log FINAL
                WHERE toDate(created_at) BETWEEN ? AND ?
                  AND project = ? AND package_id = ?
                  AND var_category = ? AND var_name = ? AND io_type = ?
                """;
        List<Map<String, Object>> rows = queryClickHouse(sql,
                start.toString(), end.toString(), project, packageId,
                varCategory, varName, ioType);
        if (rows.isEmpty()) {
            return Map.of("sample_count", 0);
        }
        Map<String, Object> row = rows.get(0);
        // 计算 missing_rate
        int sampleCount = ((Number) row.getOrDefault("sample_count", 0)).intValue();
        int missingCount = ((Number) row.getOrDefault("missing_count", 0)).intValue();
        row.put("missing_rate", sampleCount > 0 ? (double) missingCount / sampleCount : null);
        // 解析 topK 结果
        Object topValues = row.get("top_values");
        row.put("top_value", topValues != null ? AggregationJob.parseTopKValue(topValues.toString()) : null);
        // 计算 outlier_rate（基于 IQR）
        Number p25 = (Number) row.get("p25");
        Number p75 = (Number) row.get("p75");
        if (p25 != null && p75 != null) {
            double iqr = p75.doubleValue() - p25.doubleValue();
            double lower = p25.doubleValue() - 1.5 * iqr;
            double upper = p75.doubleValue() + 1.5 * iqr;
            String outlierSql = """
                    SELECT count() AS outlier_count
                    FROM execution_var_log FINAL
                    WHERE toDate(created_at) BETWEEN ? AND ?
                      AND project = ? AND package_id = ?
                      AND var_category = ? AND var_name = ? AND io_type = ?
                      AND val_num IS NOT NULL
                      AND (val_num < ? OR val_num > ?)
                    """;
            List<Map<String, Object>> outlierRows = queryClickHouse(outlierSql,
                    start.toString(), end.toString(), project, packageId,
                    varCategory, varName, ioType, lower, upper);
            if (!outlierRows.isEmpty()) {
                int outlierCount = ((Number) outlierRows.get(0).get("outlier_count")).intValue();
                int numericCount = sampleCount - missingCount;
                row.put("outlier_count", outlierCount);
                row.put("outlier_rate", numericCount > 0 ? (double) outlierCount / numericCount : null);
            }
        }
        return row;
    }

    /**
     * 通用 ClickHouse 查询，返回 List<Map>。
     */
    private List<Map<String, Object>> queryClickHouse(String sql, Object... params) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("查询 ClickHouse 失败: {}", sql, e);
        }
        return results;
    }

    // ---- 请求体模型 ----

    public static class CompareRequest {
        public String project;
        public String packageId;
        public List<CompareVariable> variables;
        public Period periodA;
        public Period periodB;
    }

    public static class CompareVariable {
        public String varCategory;
        public String varName;
        public String ioType;
    }

    public static class Period {
        public LocalDate start;
        public LocalDate end;
    }
}
