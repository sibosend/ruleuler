package com.caritasem.ruleuler.server.shadow;

import com.caritasem.ruleuler.server.auth.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 影子命中日志查询 API。
 */
@RestController
@RequestMapping("/api/shadow")
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class ShadowController {

    private static final Logger log = LoggerFactory.getLogger(ShadowController.class);

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    @GetMapping("/stats")
    public ApiResult stats(@RequestParam String project,
                           @RequestParam(required = false) String packageId,
                           @RequestParam(required = false) String ruleName,
                           @RequestParam(required = false) String startTime,
                           @RequestParam(required = false) String endTime) {
        LocalDateTime end = endTime != null ? LocalDate.parse(endTime).atTime(23, 59, 59) : LocalDateTime.now();
        LocalDateTime start = startTime != null ? LocalDate.parse(startTime).atStartOfDay() : end.minusHours(24);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    count() AS totalExecutions,
                    countIf(output_snapshot != '') AS hitCount,
                    avg(exec_ms) AS avgExecMs,
                    countIf(error_msg IS NOT NULL) AS errorCount
                FROM shadow_hit_log FINAL
                WHERE created_at BETWEEN ? AND ?
                  AND project = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        params.add(end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        params.add(project);

        if (packageId != null) {
            sql.append(" AND package_id = ?");
            params.add(packageId);
        }
        if (ruleName != null) {
            sql.append(" AND rule_name = ?");
            params.add(ruleName);
        }

        List<Map<String, Object>> rows = queryClickHouse(sql.toString(), params.toArray());
        return ApiResult.ok(rows.isEmpty() ? Map.of("totalExecutions", 0) : rows.get(0));
    }

    @GetMapping("/logs")
    public ApiResult logs(@RequestParam String project,
                          @RequestParam(required = false) String packageId,
                          @RequestParam(required = false) String ruleName,
                          @RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String endTime,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        LocalDateTime end = endTime != null ? LocalDate.parse(endTime).atTime(23, 59, 59) : LocalDateTime.now();
        LocalDateTime start = startTime != null ? LocalDate.parse(startTime).atStartOfDay() : end.minusHours(24);

        StringBuilder countSql = new StringBuilder(
                "SELECT count() AS total FROM shadow_hit_log FINAL " +
                "WHERE created_at BETWEEN ? AND ? AND project = ? ");
        StringBuilder dataSql = new StringBuilder(
                "SELECT * FROM shadow_hit_log FINAL " +
                "WHERE created_at BETWEEN ? AND ? AND project = ? ");
        List<Object> params = new ArrayList<>();
        params.add(start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        params.add(end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        params.add(project);

        if (packageId != null) {
            String cond = " AND package_id = ? ";
            countSql.append(cond);
            dataSql.append(cond);
            params.add(packageId);
        }
        if (ruleName != null) {
            String cond = " AND rule_name = ? ";
            countSql.append(cond);
            dataSql.append(cond);
            params.add(ruleName);
        }

        List<Map<String, Object>> countRows = queryClickHouse(countSql.toString(), params.toArray());
        int total = countRows.isEmpty() ? 0 : ((Number) countRows.get(0).get("total")).intValue();

        int offset = (page - 1) * size;
        dataSql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);
        List<Map<String, Object>> records = queryClickHouse(dataSql.toString(), dataParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
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
            log.error("查询 ClickHouse 失败: {}", sql, e);
        }
        return results;
    }
}
