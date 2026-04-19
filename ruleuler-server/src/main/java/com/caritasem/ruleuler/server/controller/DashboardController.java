package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.repository.RepositoryService;
import com.caritasem.ruleuler.server.auth.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private JdbcTemplate mysqlJdbc;

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    @Autowired
    @Qualifier("urule.repositoryService")
    private RepositoryService repositoryService;

    @GetMapping
    public ApiResult overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 项目数 & 知识包数
        try {
            var projects = repositoryService.loadProjects(null);
            int projectCount = projects != null ? projects.size() : 0;
            int packageCount = 0;
            List<String> projectNames = new ArrayList<>();
            if (projects != null) {
                for (var p : projects) {
                    projectNames.add(p.getName());
                    try {
                        var pkgs = repositoryService.loadProjectResourcePackages(p.getName());
                        if (pkgs != null) packageCount += pkgs.size();
                    } catch (Exception ignored) {}
                }
            }
            result.put("projectCount", projectCount);
            result.put("packageCount", packageCount);
        } catch (Exception e) {
            result.put("projectCount", 0);
            result.put("packageCount", 0);
        }

        // 2. 待审核申请数
        try {
            Integer pending = mysqlJdbc.queryForObject(
                    "SELECT COUNT(*) FROM ruleuler_publish_approval WHERE status = 'PENDING'", Integer.class);
            result.put("pendingApprovals", pending != null ? pending : 0);
        } catch (Exception e) {
            result.put("pendingApprovals", 0);
        }

        // 3. 运行中回放任务数
        try {
            Integer runningReplay = mysqlJdbc.queryForObject(
                    "SELECT COUNT(*) FROM ruleuler_replay_task WHERE status = 'running'", Integer.class);
            result.put("runningReplayTasks", runningReplay != null ? runningReplay : 0);
        } catch (Exception e) {
            result.put("runningReplayTasks", 0);
        }

        // 4. 今日执行量 & 平均耗时 & 失败数（从 ClickHouse）
        try {
            Map<String, Object> todayStats = queryClickHouseSingle("""
                SELECT
                    uniqExact(execution_id) AS exec_count,
                    avg(exec_ms) AS avg_ms,
                    countIf(var_name = '') AS error_count
                FROM execution_var_log FINAL
                WHERE toDate(created_at) = today()
                  AND io_type = 'input'
                """);
            result.put("todayExecCount", toLong(todayStats.get("exec_count")));
            result.put("todayAvgMs", toDouble(todayStats.get("avg_ms")));
            result.put("todayErrorCount", toLong(todayStats.get("error_count")));
        } catch (Exception e) {
            result.put("todayExecCount", 0);
            result.put("todayAvgMs", 0);
            result.put("todayErrorCount", 0);
        }

        // 5. 最近7天执行量趋势
        try {
            List<Map<String, Object>> trend = queryClickHouse("""
                SELECT
                    toDate(created_at) AS day,
                    uniqExact(execution_id) AS exec_count,
                    avg(exec_ms) AS avg_ms
                FROM execution_var_log FINAL
                WHERE toDate(created_at) >= today() - 6
                  AND io_type = 'input'
                GROUP BY day
                ORDER BY day
                """);
            result.put("execTrend", trend);
        } catch (Exception e) {
            result.put("execTrend", Collections.emptyList());
        }

        // 6. 最近5条上线记录
        try {
            List<Map<String, Object>> recentApprovals = mysqlJdbc.queryForList("""
                SELECT id, project, package_id AS packageId, status, submitter,
                       created_at AS createdAt, published_at AS publishedAt
                FROM ruleuler_publish_approval
                ORDER BY created_at DESC
                LIMIT 5
                """);
            result.put("recentApprovals", recentApprovals);
        } catch (Exception e) {
            result.put("recentApprovals", Collections.emptyList());
        }

        // 7. 最近5条回放任务
        try {
            List<Map<String, Object>> recentReplays = mysqlJdbc.queryForList("""
                SELECT id, project, package_id AS packageId, status,
                       total_count AS totalCount, match_count AS matchCount,
                       mismatch_count AS mismatchCount, created_at AS createdAt
                FROM ruleuler_replay_task
                ORDER BY created_at DESC
                LIMIT 5
                """);
            result.put("recentReplays", recentReplays);
        } catch (Exception e) {
            result.put("recentReplays", Collections.emptyList());
        }

        // 8. 变量监控告警数（今日有异常标记的变量数）
        try {
            Map<String, Object> alertStats = queryClickHouseSingle("""
                SELECT count(DISTINCT concat(var_category, '.', var_name)) AS alert_count
                FROM execution_var_log_5m
                WHERE toDate(window_start) = today()
                  AND error_count > 0
                """);
            result.put("todayAlertCount", toLong(alertStats.get("alert_count")));
        } catch (Exception e) {
            result.put("todayAlertCount", 0);
        }

        return ApiResult.ok(result);
    }

    // ---- ClickHouse helpers ----

    private List<Map<String, Object>> queryClickHouse(String sql, Object... params) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = clickHouseDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                var meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private Map<String, Object> queryClickHouseSingle(String sql, Object... params) throws Exception {
        List<Map<String, Object>> rows = queryClickHouse(sql, params);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
