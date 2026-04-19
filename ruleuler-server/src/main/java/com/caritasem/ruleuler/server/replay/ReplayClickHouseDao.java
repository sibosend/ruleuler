package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.server.replay.model.TrafficQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Repository
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class ReplayClickHouseDao {

    private static final Logger log = LoggerFactory.getLogger(ReplayClickHouseDao.class);

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDs;

    public long countDistinctExecutions(TrafficQuery query) {
        String sql = """
                SELECT count(DISTINCT execution_id) AS total
                FROM execution_var_log FINAL
                WHERE toDate(created_at) BETWEEN ? AND ?
                  AND project = ? AND package_id IN (?, ?)
                  AND var_name != ''
                """;
        String startDate = toLocalDate(query.getStartTime()).toString();
        String endDate = toLocalDate(query.getEndTime()).toString();
        String fullPkgId = query.getProject() + "/" + query.getPackageId();
        List<Map<String, Object>> rows = queryClickHouse(sql,
                startDate, endDate, query.getProject(), query.getPackageId(), fullPkgId);
        if (rows.isEmpty()) return 0;
        Object total = rows.get(0).get("total");
        return total != null ? ((Number) total).longValue() : 0;
    }

    public List<String> fetchRandomSample(TrafficQuery query, long totalCount) {
        int sampleSize = Math.min(query.getSampleSize(), (int) totalCount);
        if (sampleSize <= 0) return Collections.emptyList();
        String sql = """
                SELECT DISTINCT execution_id FROM execution_var_log FINAL
                WHERE toDate(created_at) BETWEEN ? AND ?
                  AND project = ? AND package_id IN (?, ?)
                  AND var_name != ''
                ORDER BY rand()
                LIMIT ?
                """;
        String startDate = toLocalDate(query.getStartTime()).toString();
        String endDate = toLocalDate(query.getEndTime()).toString();
        String fullPkgId = query.getProject() + "/" + query.getPackageId();
        List<Map<String, Object>> rows = queryClickHouse(sql,
                startDate, endDate, query.getProject(), query.getPackageId(), fullPkgId, sampleSize);
        List<String> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ids.add(String.valueOf(row.get("execution_id")));
        }
        return ids;
    }

    public List<String> fetchUniformSample(TrafficQuery query) {
        int n = query.getSampleSize();
        String sql = """
                SELECT execution_id FROM (
                    SELECT execution_id,
                           min(created_at) AS t,
                           row_number() OVER (ORDER BY min(created_at)) AS rn,
                           count() OVER () AS total
                    FROM execution_var_log FINAL
                    WHERE toDate(created_at) BETWEEN ? AND ?
                      AND project = ? AND package_id IN (?, ?)
                      AND var_name != ''
                    GROUP BY execution_id
                ) WHERE total <= ? OR rn % greatest(1, intDivOrZero(total, ?)) = 0
                LIMIT ?
                """;
        String startDate = toLocalDate(query.getStartTime()).toString();
        String endDate = toLocalDate(query.getEndTime()).toString();
        String fullPkgId = query.getProject() + "/" + query.getPackageId();
        List<Map<String, Object>> rows = queryClickHouse(sql,
                startDate, endDate, query.getProject(), query.getPackageId(), fullPkgId,
                n, n, n);
        List<String> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ids.add(String.valueOf(row.get("execution_id")));
        }
        return ids;
    }

    public List<String> fetchAllExecutionIds(TrafficQuery query) {
        String sql = """
                SELECT DISTINCT execution_id FROM execution_var_log FINAL
                WHERE toDate(created_at) BETWEEN ? AND ?
                  AND project = ? AND package_id IN (?, ?)
                  AND var_name != ''
                ORDER BY created_at DESC
                """;
        String startDate = toLocalDate(query.getStartTime()).toString();
        String endDate = toLocalDate(query.getEndTime()).toString();
        String fullPkgId = query.getProject() + "/" + query.getPackageId();
        List<Map<String, Object>> rows = queryClickHouse(sql,
                startDate, endDate, query.getProject(), query.getPackageId(), fullPkgId);
        List<String> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ids.add(String.valueOf(row.get("execution_id")));
        }
        return ids;
    }

    public List<Map<String, Object>> fetchVarRows(String executionId) {
        String sql = """
                SELECT execution_id, project, package_id, flow_id,
                    var_category, var_name, var_type,
                    val_num, val_str, io_type, exec_ms, created_at
                FROM execution_var_log FINAL
                WHERE execution_id = ?
                ORDER BY var_category, var_name, io_type
                """;
        return queryClickHouse(sql, executionId);
    }

    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
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
