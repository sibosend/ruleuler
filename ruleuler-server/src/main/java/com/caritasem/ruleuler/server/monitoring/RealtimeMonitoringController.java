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
import java.util.*;
import java.util.stream.DoubleStream;

@RestController
@RequestMapping("/api/monitoring/realtime")
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class RealtimeMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMonitoringController.class);

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    @Autowired
    private AlertConfigService alertConfigService;

    @Autowired
    private AlertChecker alertChecker;

    @Autowired
    private JdbcTemplate mysqlJdbcTemplate;

    @GetMapping("/dashboard")
    public ApiResult dashboard(@RequestParam String project,
                               @RequestParam String packageId) {
        String sql = """
            SELECT
                if(toDate(window_start) = today(), 'today', 'yesterday') AS day_type,
                sum(sample_count) AS total_executions,
                sum(error_count) AS error_executions,
                sum(missing_count) AS missing_executions
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?)
              AND io_type = 'input'
              AND toDate(window_start) IN (today(), yesterday())
            GROUP BY day_type
        """;

        List<Map<String, Object>> rows = queryClickHouse(sql, project, packageId, project + "/" + packageId);
        
        Map<String, Object> today = new HashMap<>();
        today.put("total_executions", 0);
        today.put("anomaly_rate", 0.0);
        today.put("error_rate", 0.0);

        Map<String, Object> yesterday = new HashMap<>();
        yesterday.put("total_executions", 0);
        yesterday.put("anomaly_rate", 0.0);
        yesterday.put("error_rate", 0.0);

        for (Map<String, Object> row : rows) {
            String dayType = (String) row.get("day_type");
            long total = ((Number) row.get("total_executions")).longValue();
            long errors = ((Number) row.get("error_executions")).longValue();
            long missing = ((Number) row.get("missing_executions")).longValue();
            
            double errRate = total > 0 ? (double) errors / total : 0.0;
            double anomalyRate = total > 0 ? (double) missing / total : 0.0;

            Map<String, Object> target = "today".equals(dayType) ? today : yesterday;
            target.put("total_executions", total);
            target.put("error_rate", errRate);
            target.put("anomaly_rate", anomalyRate);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("today", today);
        result.put("yesterday", yesterday);
        return ApiResult.ok(result);
    }

    @GetMapping("/variables")
    public ApiResult variables(@RequestParam String project,
                               @RequestParam String packageId,
                               @RequestParam(defaultValue = "input") String ioType,
                               @RequestParam(defaultValue = "anomaly") String sortBy) {
        String sql = """
            SELECT
                var_category, var_name, any(var_type) as var_type,
                sum(sample_count) AS sample_count,
                sum(missing_count) AS missing_count,
                sum(sum_val_num) AS sum_val_num,
                sum(error_count) AS error_count,
                min(min_val_num) AS min_val_num,
                max(max_val_num) AS max_val_num
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?) AND io_type = ?
              AND toDate(window_start) = today()
            GROUP BY var_category, var_name
        """;

        List<Map<String, Object>> rows = queryClickHouse(sql, project, packageId, project + "/" + packageId, ioType);
        
        List<Map<String, Object>> enhancedRows = new ArrayList<>();
        AlertConfig config = alertConfigService.getCachedConfig();

        for (Map<String, Object> row : rows) {
            long samples = ((Number) row.get("sample_count")).longValue();
            long missing = ((Number) row.get("missing_count")).longValue();
            long errors = ((Number) row.get("error_count")).longValue();
            Double sumValNum = (Double) row.get("sum_val_num");

            Double missingRate = samples > 0 ? (double) missing / samples : null;
            Double errorRate = samples > 0 ? (double) errors / samples : null;
            Double mean = null;
            long numericSamples = samples - missing;
            if (numericSamples > 0 && sumValNum != null) {
                mean = sumValNum / numericSamples;
            }

            row.put("missing_rate", missingRate);
            row.put("error_rate", errorRate);
            row.put("mean", mean);

            // Re-use AlertChecker check function for flags
            String flags = AlertChecker.check(missingRate, null, null,
                    config.getMissingRateMax(), config.getOutlierRateMax(), config.getSkewnessAbsMax());
            row.put("alert_flags", flags);
            enhancedRows.add(row);
        }

        // Sort logic
        enhancedRows.sort((a, b) -> {
            boolean aAlert = a.get("alert_flags") != null;
            boolean bAlert = b.get("alert_flags") != null;
            
            if ("anomaly".equals(sortBy)) {
                if (aAlert != bAlert) return aAlert ? -1 : 1;
                Double aMiss = (Double) a.get("missing_rate");
                Double bMiss = (Double) b.get("missing_rate");
                return Double.compare(bMiss != null ? bMiss : 0.0, aMiss != null ? aMiss : 0.0);
            } else if ("missing_rate".equals(sortBy)) {
                Double aMiss = (Double) a.get("missing_rate");
                Double bMiss = (Double) b.get("missing_rate");
                return Double.compare(bMiss != null ? bMiss : 0.0, aMiss != null ? aMiss : 0.0);
            } else if ("error_rate".equals(sortBy)) {
                Double aErr = (Double) a.get("error_rate");
                Double bErr = (Double) b.get("error_rate");
                return Double.compare(bErr != null ? bErr : 0.0, aErr != null ? aErr : 0.0);
            } else {
                Long aSamp = (Long) a.get("sample_count");
                Long bSamp = (Long) b.get("sample_count");
                return Long.compare(bSamp != null ? bSamp : 0L, aSamp != null ? aSamp : 0L);
            }
        });

        return ApiResult.ok(enhancedRows);
    }

    @GetMapping("/missing-rate-trend")
    public ApiResult missingRateTrend(@RequestParam String project,
                                      @RequestParam String packageId,
                                      @RequestParam String varCategory,
                                      @RequestParam String varName,
                                      @RequestParam(defaultValue = "input") String ioType) {
        String sql = """
            SELECT
                window_start,
                sample_count,
                missing_count
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?) AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(window_start) = today()
            ORDER BY window_start ASC
        """;

        List<Map<String, Object>> rows = queryClickHouse(sql, project, packageId, project + "/" + packageId, varCategory, varName, ioType);
        AlertConfig config = alertConfigService.getCachedConfig();

        List<Map<String, Object>> results = new ArrayList<>();
        Double prevRate = null;

        for (Map<String, Object> row : rows) {
            long samples = ((Number) row.get("sample_count")).longValue();
            long missing = ((Number) row.get("missing_count")).longValue();
            Double rate = samples > 0 ? (double) missing / samples : 0.0;
            
            boolean spike = false;
            if (prevRate != null && (rate - prevRate) > config.getMissingRateSpikeDelta()) {
                spike = true;
            }

            Map<String, Object> res = new HashMap<>();
            res.put("window_start", row.get("window_start"));
            res.put("sample_count", samples);
            res.put("missing_rate", rate);
            res.put("spike", spike);
            results.add(res);

            prevRate = rate;
        }

        return ApiResult.ok(results);
    }

    /**
     * PSI 分布稳定性指数。
     * Feature: monitoring-realtime-enhancement, Requirements 8
     */
    @GetMapping("/psi")
    public ApiResult psi(@RequestParam String project,
                         @RequestParam String packageId,
                         @RequestParam String varCategory,
                         @RequestParam String varName,
                         @RequestParam(defaultValue = "input") String ioType) {
        // 1. 查物化视图获取 var_type，判断是否类别型
        String varTypeSql = """
            SELECT any(var_type) AS var_type
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?) AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(window_start) = today()
        """;
        List<Map<String, Object>> varTypeRows = queryClickHouse(varTypeSql,
                project, packageId, project + "/" + packageId, varCategory, varName, ioType);

        if (!varTypeRows.isEmpty()) {
            String varType = (String) varTypeRows.get(0).get("var_type");
            if (PsiCalculator.isCategorical(varType)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("psi", null);
                result.put("psi_alert", false);
                result.put("psi_warning", false);
                result.put("bins", List.of());
                return ApiResult.ok(result);
            }
        }

        // 2. 基准分布：过去 30 天 ClickHouse 原始 val_num
        String baselineSql = """
            SELECT val_num
            FROM execution_var_log
            WHERE project = ? AND package_id IN (?, ?) AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(created_at) BETWEEN today() - 30 AND yesterday()
              AND val_num IS NOT NULL
        """;
        List<Map<String, Object>> baselineRows = queryClickHouse(baselineSql,
                project, packageId, project + "/" + packageId, varCategory, varName, ioType);

        // 3. 当前分布：当日原始 val_num
        String currentSql = """
            SELECT val_num
            FROM execution_var_log
            WHERE project = ? AND package_id IN (?, ?) AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(created_at) = today()
              AND val_num IS NOT NULL
        """;
        List<Map<String, Object>> currentRows = queryClickHouse(currentSql,
                project, packageId, project + "/" + packageId, varCategory, varName, ioType);

        // 4. 数据不足时返回 null
        if (baselineRows.isEmpty() || currentRows.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("psi", null);
            result.put("psi_alert", false);
            result.put("psi_warning", false);
            result.put("bins", List.of());
            return ApiResult.ok(result);
        }

        double[] baselineValues = baselineRows.stream()
                .mapToDouble(r -> ((Number) r.get("val_num")).doubleValue())
                .toArray();
        double[] currentValues = currentRows.stream()
                .mapToDouble(r -> ((Number) r.get("val_num")).doubleValue())
                .toArray();

        // 5. 计算 PSI，阈值从 AlertConfig 读取
        AlertConfig config = alertConfigService.getCachedConfig();
        PsiCalculator.PsiResult psiResult = PsiCalculator.computeWithThresholds(
                baselineValues, currentValues, config.getPsiWarning(), config.getPsiAlert());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("psi", psiResult.psi());
        result.put("psi_alert", psiResult.psiAlert());
        result.put("psi_warning", psiResult.psiWarning());
        result.put("bins", psiResult.bins());
        return ApiResult.ok(result);
    }

    /**
     * 枚举字段漂移检测。
     * Feature: monitoring-realtime-enhancement, Requirements 9
     */
    @GetMapping("/enum-drift")
    public ApiResult enumDrift(@RequestParam String project,
                               @RequestParam String packageId,
                               @RequestParam(defaultValue = "input") String ioType) {
        // 1. 从 ClickHouse 查当日类别型变量的 val_str 频率分布
        String varTypeIn = AggregationJob.CATEGORICAL_TYPES.stream()
                .map(t -> "'" + t + "'")
                .collect(java.util.stream.Collectors.joining(","));

        String currentSql = """
            SELECT var_category, var_name, val_str, count() AS cnt
            FROM execution_var_log
            WHERE project = ? AND package_id IN (?, ?) AND io_type = ?
              AND toDate(created_at) = today()
              AND var_type IN (%s)
              AND var_name != ''
            GROUP BY var_category, var_name, val_str
            ORDER BY var_category, var_name, cnt DESC
        """.formatted(varTypeIn);

        List<Map<String, Object>> currentRows = queryClickHouse(currentSql,
                project, packageId, project + "/" + packageId, ioType);

        // 按 (var_category, var_name) 分组，取第一行作为 top_value
        record VarKey(String varCategory, String varName) {}
        Map<VarKey, String> currentTopValue = new LinkedHashMap<>();
        Map<VarKey, Double> currentTopFreqRatio = new LinkedHashMap<>();
        Map<VarKey, Long> varTotalCount = new LinkedHashMap<>();

        for (Map<String, Object> row : currentRows) {
            String vc = (String) row.get("var_category");
            String vn = (String) row.get("var_name");
            VarKey key = new VarKey(vc, vn);
            long cnt = ((Number) row.get("cnt")).longValue();
            varTotalCount.merge(key, cnt, Long::sum);
            currentTopValue.putIfAbsent(key, (String) row.get("val_str"));
        }

        // 计算 top_freq_ratio = top_count / total
        for (Map.Entry<VarKey, String> entry : currentTopValue.entrySet()) {
            VarKey key = entry.getKey();
            String topVal = entry.getValue();
            long total = varTotalCount.getOrDefault(key, 0L);
            // 重新统计 top_value 的 count
            long topCount = currentRows.stream()
                    .filter(r -> key.varCategory().equals(r.get("var_category"))
                            && key.varName().equals(r.get("var_name"))
                            && Objects.equals(topVal, r.get("val_str")))
                    .mapToLong(r -> ((Number) r.get("cnt")).longValue())
                    .sum();
            currentTopFreqRatio.put(key, total > 0 ? (double) topCount / total : 0.0);
        }

        // 2. 从 MySQL daily_stats 获取过去 7 天基准
        String baselineSql = """
            SELECT var_category, var_name, stat_date, top_value, top_freq_ratio
            FROM ruleuler_variable_daily_stats
            WHERE project = ? AND package_id = ? AND io_type = ?
              AND var_type IN (%s)
              AND stat_date BETWEEN CURDATE() - INTERVAL 7 DAY AND CURDATE() - INTERVAL 1 DAY
            ORDER BY stat_date DESC
        """.formatted(varTypeIn);

        List<Map<String, Object>> baselineRows = mysqlJdbcTemplate.queryForList(
                baselineSql, project, packageId, ioType);

        // 按 (var_category, var_name) 分组：AVG(top_freq_ratio) 和最近一天的 top_value
        Map<VarKey, String> baselineTopValue = new LinkedHashMap<>();
        Map<VarKey, List<Double>> baselineRatios = new LinkedHashMap<>();

        for (Map<String, Object> row : baselineRows) {
            String vc = (String) row.get("var_category");
            String vn = (String) row.get("var_name");
            VarKey key = new VarKey(vc, vn);
            // 最近一天的 top_value（ORDER BY stat_date DESC，第一条即最新）
            baselineTopValue.putIfAbsent(key, (String) row.get("top_value"));
            Double ratio = (Double) row.get("top_freq_ratio");
            if (ratio != null) {
                baselineRatios.computeIfAbsent(key, k -> new ArrayList<>()).add(ratio);
            }
        }

        Map<VarKey, Double> baselineAvgRatio = new LinkedHashMap<>();
        for (Map.Entry<VarKey, List<Double>> entry : baselineRatios.entrySet()) {
            List<Double> ratios = entry.getValue();
            double avg = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            baselineAvgRatio.put(entry.getKey(), avg);
        }

        // 3. 获取阈值
        double threshold = alertConfigService.getCachedConfig().getEnumDriftThreshold();

        // 4. 对每个当日类别型变量调用 EnumDriftDetector.detect()
        List<EnumDriftDetector.DriftResult> results = new ArrayList<>();
        for (VarKey key : currentTopValue.keySet()) {
            String curTop = currentTopValue.get(key);
            double curRatio = currentTopFreqRatio.getOrDefault(key, 0.0);
            String baseTop = baselineTopValue.getOrDefault(key, null);
            double baseRatio = baselineAvgRatio.getOrDefault(key, 0.0);

            results.add(EnumDriftDetector.detect(
                    key.varCategory(), key.varName(),
                    curTop, curRatio,
                    baseTop, baseRatio,
                    threshold));
        }

        return ApiResult.ok(results);
    }

    /**
     * 版本对比：按规则包版本号对比发版前后的变量指标变化。
     * Feature: monitoring-realtime-enhancement, Requirements 10
     */
    @PostMapping("/version-compare")
    public ApiResult versionCompare(@RequestBody Map<String, String> body) {
        String project = body.get("project");
        String packageId = body.get("packageId");
        String versionA = body.get("versionA");
        String versionB = body.get("versionB");

        if (project == null || packageId == null || versionA == null || versionB == null) {
            return ApiResult.error(400, "缺少必填参数: project, packageId, versionA, versionB");
        }

        // 从 MySQL 查版本发布时间（created_at 为毫秒时间戳）
        String versionSql = """
            SELECT v.version_name, v.created_at
            FROM ruleuler_rule_file_version v
            JOIN ruleuler_rule_file f ON v.file_id = f.id
            WHERE f.project = ? AND v.version_name IN (?, ?)
            ORDER BY v.created_at ASC
        """;
        List<Map<String, Object>> versions = mysqlJdbcTemplate.queryForList(
                versionSql, project, versionA, versionB);

        if (versions.size() < 2) {
            return ApiResult.error(404, "未找到指定版本数据");
        }

        // 按 created_at 排序后，确定两个版本的时间范围
        long timeA = ((Number) versions.get(0).get("created_at")).longValue();
        long timeB = ((Number) versions.get(1).get("created_at")).longValue();

        // 检查 TTL（3 个月）
        long threeMonthsAgoMs = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;
        if (timeA < threeMonthsAgoMs) {
            return ApiResult.error(404, "版本 " + versions.get(0).get("version_name") + " 数据已超出3个月TTL");
        }

        // Period A: timeA → timeB, Period B: timeB → now
        // 转换为秒给 ClickHouse DateTime
        long periodAStartSec = timeA / 1000;
        long periodAEndSec = timeB / 1000;
        long periodBStartSec = timeB / 1000;
        long periodBEndSec = System.currentTimeMillis() / 1000;

        String statsSql = """
            SELECT var_category, var_name,
                   sum(sample_count) AS sample_count,
                   sum(missing_count) AS missing_count,
                   sum(sum_val_num) AS sum_val_num,
                   sum(error_count) AS error_count,
                   min(min_val_num) AS min_val_num,
                   max(max_val_num) AS max_val_num
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?)
              AND window_start >= toDateTime(?) AND window_start < toDateTime(?)
            GROUP BY var_category, var_name
        """;

        List<Map<String, Object>> periodARows = queryClickHouse(statsSql,
                project, packageId, project + "/" + packageId, periodAStartSec, periodAEndSec);
        List<Map<String, Object>> periodBRows = queryClickHouse(statsSql,
                project, packageId, project + "/" + packageId, periodBStartSec, periodBEndSec);

        // 构建 periodB 索引
        Map<String, Map<String, Object>> periodBIndex = new HashMap<>();
        for (Map<String, Object> row : periodBRows) {
            String key = row.get("var_category") + "|" + row.get("var_name");
            periodBIndex.put(key, computeDerivedMetrics(row));
        }

        // 合并结果
        Set<String> allKeys = new LinkedHashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> row : periodARows) {
            String key = row.get("var_category") + "|" + row.get("var_name");
            allKeys.add(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("var_category", row.get("var_category"));
            item.put("var_name", row.get("var_name"));
            item.put("periodA", computeDerivedMetrics(row));
            item.put("periodB", periodBIndex.getOrDefault(key, Map.of()));
            results.add(item);
        }

        // periodB 中有但 periodA 中没有的变量
        for (Map<String, Object> row : periodBRows) {
            String key = row.get("var_category") + "|" + row.get("var_name");
            if (!allKeys.contains(key)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("var_category", row.get("var_category"));
                item.put("var_name", row.get("var_name"));
                item.put("periodA", Map.of());
                item.put("periodB", computeDerivedMetrics(row));
                results.add(item);
            }
        }

        return ApiResult.ok(results);
    }

    /** 从聚合行计算派生指标 */
    private Map<String, Object> computeDerivedMetrics(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>(row);
        long samples = ((Number) row.get("sample_count")).longValue();
        long missing = ((Number) row.get("missing_count")).longValue();
        long errors = ((Number) row.get("error_count")).longValue();
        Object sumObj = row.get("sum_val_num");
        double sumValNum = sumObj != null ? ((Number) sumObj).doubleValue() : 0.0;

        m.put("missing_rate", samples > 0 ? (double) missing / samples : null);
        m.put("error_rate", samples > 0 ? (double) errors / samples : null);
        long numericSamples = samples - missing;
        m.put("mean", numericSamples > 0 ? sumValNum / numericSamples : null);
        return m;
    }

    /**
     * 异常记录下钻：根据变量名和异常类型查询 ClickHouse 中的 execution_id 列表。
     * Feature: monitoring-realtime-enhancement, Requirements 11
     */
    @GetMapping("/anomaly-records")
    public ApiResult anomalyRecords(@RequestParam String project,
                                     @RequestParam String packageId,
                                     @RequestParam String varCategory,
                                     @RequestParam String varName,
                                     @RequestParam(defaultValue = "input") String ioType,
                                     @RequestParam(defaultValue = "missing") String anomalyType,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize) {

        String anomalyCondition = switch (anomalyType) {
            case "missing" -> "val_num IS NULL AND val_str IS NULL";
            case "error" -> "var_name = ''";
            case "outlier" -> "val_num IS NOT NULL"; // outlier 需后续 IQR 过滤，先返回有值记录
            default -> "1=0"; // 未知类型不返回数据
        };

        // 总数
        String countSql = """
            SELECT count() AS total
            FROM execution_var_log
            WHERE project = ? AND package_id IN (?, ?)
              AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(created_at) = today()
              AND %s
        """.formatted(anomalyCondition);

        List<Map<String, Object>> countRows = queryClickHouse(countSql,
                project, packageId, project + "/" + packageId, varCategory, varName, ioType);
        long total = countRows.isEmpty() ? 0 : ((Number) countRows.get(0).get("total")).longValue();

        // 分页查询
        int offset = (page - 1) * pageSize;
        String recordsSql = """
            SELECT execution_id, created_at, val_num, val_str
            FROM execution_var_log
            WHERE project = ? AND package_id IN (?, ?)
              AND var_category = ? AND var_name = ? AND io_type = ?
              AND toDate(created_at) = today()
              AND %s
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.formatted(anomalyCondition);

        List<Map<String, Object>> records = queryClickHouse(recordsSql,
                project, packageId, project + "/" + packageId, varCategory, varName, ioType,
                pageSize, offset);

        for (Map<String, Object> r : records) {
            r.put("anomaly_type", anomalyType);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ApiResult.ok(result);
    }

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
            log.error("ClickHouse Query Failed: {}", sql, e);
            throw new RuntimeException("DB Query Failed");
        }
        return results;
    }

    /**
     * 近N日执行量走势（执行量 + 异常率/错误率）。
     */
    /**
     * 分时走势（5分钟粒度），支持指定日期和前一天对比。
     * Feature: monitoring-execution-trend, Requirements 1.1-1.6
     */
    @GetMapping("/intraday-trend")
    public ApiResult intradayTrend(@RequestParam String project,
                                   @RequestParam String packageId,
                                   @RequestParam(required = false) String date) {
        // 参数校验
        if (project == null || project.isBlank()) {
            return ApiResult.error(400, "project is required");
        }
        if (packageId == null || packageId.isBlank()) {
            return ApiResult.error(400, "packageId is required");
        }

        // 日期处理：默认当天
        String targetDate = (date != null && !date.isBlank()) ? date : java.time.LocalDate.now().toString();
        String previousDate = java.time.LocalDate.parse(targetDate).minusDays(1).toString();

        String sql = """
            SELECT
                formatDateTime(window_start, '%H:%M') AS window_time,
                sum(sample_count) AS sample_count,
                sum(missing_count) AS missing_count,
                sum(error_count) AS error_count,
                ?
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?)
              AND io_type = 'input'
              AND toDate(window_start) = ?
            GROUP BY window_time
            ORDER BY window_time ASC
        """;

        List<Map<String, Object>> results = new ArrayList<>();

        // 查询目标日
        List<Map<String, Object>> targetRows = queryClickHouse(sql, "target", project, packageId, project + "/" + packageId, targetDate);
        for (Map<String, Object> row : targetRows) {
            long sample = ((Number) row.get("sample_count")).longValue();
            long missing = ((Number) row.get("missing_count")).longValue();
            long error = ((Number) row.get("error_count")).longValue();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("window_start", row.get("window_time"));
            item.put("sample_count", sample);
            item.put("missing_count", missing);
            item.put("error_count", error);
            item.put("anomaly_rate", sample > 0 ? (double) missing / sample : 0.0);
            item.put("error_rate", sample > 0 ? (double) error / sample : 0.0);
            item.put("day_type", "target");
            results.add(item);
        }

        // 查询前一天
        List<Map<String, Object>> previousRows = queryClickHouse(sql, "previous", project, packageId, project + "/" + packageId, previousDate);
        for (Map<String, Object> row : previousRows) {
            long sample = ((Number) row.get("sample_count")).longValue();
            long missing = ((Number) row.get("missing_count")).longValue();
            long error = ((Number) row.get("error_count")).longValue();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("window_start", row.get("window_time"));
            item.put("sample_count", sample);
            item.put("missing_count", missing);
            item.put("error_count", error);
            item.put("anomaly_rate", sample > 0 ? (double) missing / sample : 0.0);
            item.put("error_rate", sample > 0 ? (double) error / sample : 0.0);
            item.put("day_type", "previous");
            results.add(item);
        }

        return ApiResult.ok(results);
    }

    @GetMapping("/daily-trend")
    public ApiResult dailyTrend(@RequestParam String project,
                                @RequestParam String packageId,
                                @RequestParam(defaultValue = "14") int days) {
        String sql = """
            SELECT
                toDate(window_start) AS stat_date,
                sum(sample_count) AS total_executions,
                sum(error_count) AS error_executions,
                sum(missing_count) AS missing_executions
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?)
              AND io_type = 'input'
              AND toDate(window_start) >= today() - ?
            GROUP BY stat_date
            ORDER BY stat_date ASC
        """;

        List<Map<String, Object>> rows = queryClickHouse(sql, project, packageId, project + "/" + packageId, days);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long total = ((Number) row.get("total_executions")).longValue();
            long errors = ((Number) row.get("error_executions")).longValue();
            long missing = ((Number) row.get("missing_executions")).longValue();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stat_date", row.get("stat_date").toString());
            item.put("total_executions", total);
            item.put("error_executions", errors);
            item.put("missing_executions", missing);
            item.put("error_rate", total > 0 ? (double) errors / total : 0.0);
            item.put("anomaly_rate", total > 0 ? (double) missing / total : 0.0);
            results.add(item);
        }

        return ApiResult.ok(results);
    }

    /**
     * 变量列表 + DoD/WoW 对比数据。
     * Feature: monitoring-realtime-enhancement, Requirements 7.1, 7.2
     */
    @GetMapping("/variables-with-comparison")
    public ApiResult variablesWithComparison(@RequestParam String project,
                                              @RequestParam String packageId,
                                              @RequestParam(defaultValue = "input") String ioType,
                                              @RequestParam(defaultValue = "anomaly") String sortBy) {
        // 1. 从 ClickHouse 物化视图获取当日实时数据
        String realtimeSql = """
            SELECT
                var_category, var_name, any(var_type) as var_type,
                sum(sample_count) AS sample_count,
                sum(missing_count) AS missing_count,
                sum(sum_val_num) AS sum_val_num,
                sum(error_count) AS error_count,
                min(min_val_num) AS min_val_num,
                max(max_val_num) AS max_val_num
            FROM execution_var_log_5m
            WHERE project = ? AND package_id IN (?, ?) AND io_type = ?
              AND toDate(window_start) = today()
            GROUP BY var_category, var_name
        """;

        List<Map<String, Object>> realtimeRows = queryClickHouse(realtimeSql, project, packageId, project + "/" + packageId, ioType);

        // 2. 从 MySQL daily_stats 获取昨日和上周基准数据
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastWeek = today.minusDays(7);

        String baselineSql = """
            SELECT var_category, var_name,
                   stat_date,
                   mean AS baseline_mean,
                   missing_rate AS baseline_missing_rate
            FROM ruleuler_variable_daily_stats
            WHERE project = ? AND package_id = ? AND io_type = ?
              AND stat_date IN (?, ?)
        """;

        List<Map<String, Object>> baselineRows = mysqlJdbcTemplate.queryForList(
                baselineSql, project, packageId, ioType, yesterday, lastWeek);

        // 3. 构建基准数据索引：(varCategory|varName|statDate) -> baseline data
        Map<String, Map<String, Object>> baselineIndex = new HashMap<>();
        for (Map<String, Object> row : baselineRows) {
            String varCategory = (String) row.get("var_category");
            String varName = (String) row.get("var_name");
            java.sql.Date statDate = (java.sql.Date) row.get("stat_date");
            String key = varCategory + "|" + varName + "|" + statDate.toLocalDate();
            baselineIndex.put(key, row);
        }

        // 4. 合并数据并计算 DoD/WoW
        List<Map<String, Object>> results = new ArrayList<>();
        AlertConfig config = alertConfigService.getCachedConfig();

        for (Map<String, Object> row : realtimeRows) {
            String varCategory = (String) row.get("var_category");
            String varName = (String) row.get("var_name");

            // 计算实时指标
            long samples = ((Number) row.get("sample_count")).longValue();
            long missing = ((Number) row.get("missing_count")).longValue();
            long errors = ((Number) row.get("error_count")).longValue();
            Double sumValNum = (Double) row.get("sum_val_num");

            Double missingRate = samples > 0 ? (double) missing / samples : null;
            Double errorRate = samples > 0 ? (double) errors / samples : null;
            Double mean = null;
            long numericSamples = samples - missing;
            if (numericSamples > 0 && sumValNum != null) {
                mean = sumValNum / numericSamples;
            }

            row.put("missing_rate", missingRate);
            row.put("error_rate", errorRate);
            row.put("mean", mean);

            // 获取昨日基准
            String yesterdayKey = varCategory + "|" + varName + "|" + yesterday;
            Map<String, Object> yesterdayData = baselineIndex.get(yesterdayKey);
            Double yesterdayMean = yesterdayData != null ? (Double) yesterdayData.get("baseline_mean") : null;
            Double yesterdayMissingRate = yesterdayData != null ? (Double) yesterdayData.get("baseline_missing_rate") : null;

            // 获取上周基准
            String lastWeekKey = varCategory + "|" + varName + "|" + lastWeek;
            Map<String, Object> lastWeekData = baselineIndex.get(lastWeekKey);
            Double lastWeekMean = lastWeekData != null ? (Double) lastWeekData.get("baseline_mean") : null;
            Double lastWeekMissingRate = lastWeekData != null ? (Double) lastWeekData.get("baseline_missing_rate") : null;

            // 计算 DoD/WoW 百分比
            Double dodMeanPct = DiffPctCalculator.computeDiffPct(yesterdayMean, mean);
            Double dodMissingRatePct = DiffPctCalculator.computeDiffPct(yesterdayMissingRate, missingRate);
            Double wowMeanPct = DiffPctCalculator.computeDiffPct(lastWeekMean, mean);
            Double wowMissingRatePct = DiffPctCalculator.computeDiffPct(lastWeekMissingRate, missingRate);

            row.put("dod_mean_pct", dodMeanPct);
            row.put("dod_missing_rate_pct", dodMissingRatePct);
            row.put("wow_mean_pct", wowMeanPct);
            row.put("wow_missing_rate_pct", wowMissingRatePct);
            row.put("yesterday_mean", yesterdayMean);
            row.put("last_week_mean", lastWeekMean);

            // 告警标记
            String flags = AlertChecker.check(missingRate, null, null,
                    config.getMissingRateMax(), config.getOutlierRateMax(), config.getSkewnessAbsMax());
            row.put("alert_flags", flags);

            results.add(row);
        }

        // 5. 排序
        results.sort((a, b) -> {
            boolean aAlert = a.get("alert_flags") != null;
            boolean bAlert = b.get("alert_flags") != null;

            if ("anomaly".equals(sortBy)) {
                if (aAlert != bAlert) return aAlert ? -1 : 1;
                Double aMiss = (Double) a.get("missing_rate");
                Double bMiss = (Double) b.get("missing_rate");
                return Double.compare(bMiss != null ? bMiss : 0.0, aMiss != null ? aMiss : 0.0);
            } else if ("missing_rate".equals(sortBy)) {
                Double aMiss = (Double) a.get("missing_rate");
                Double bMiss = (Double) b.get("missing_rate");
                return Double.compare(bMiss != null ? bMiss : 0.0, aMiss != null ? aMiss : 0.0);
            } else if ("error_rate".equals(sortBy)) {
                Double aErr = (Double) a.get("error_rate");
                Double bErr = (Double) b.get("error_rate");
                return Double.compare(bErr != null ? bErr : 0.0, aErr != null ? aErr : 0.0);
            } else {
                Long aSamp = (Long) a.get("sample_count");
                Long bSamp = (Long) b.get("sample_count");
                return Long.compare(bSamp != null ? bSamp : 0L, aSamp != null ? aSamp : 0L);
            }
        });

        return ApiResult.ok(results);
    }
}
