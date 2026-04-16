package com.caritasem.ruleuler.server.grayscale;

import com.caritasem.ruleuler.server.grayscale.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class GrayscaleRuleDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<GrayscaleRule> RULE_MAPPER = (rs, rowNum) -> GrayscaleRule.builder()
            .id(rs.getLong("id"))
            .project(rs.getString("project"))
            .packageId(rs.getString("package_id"))
            .approvalId(rs.getLong("approval_id"))
            .snapshotId(rs.getLong("snapshot_id"))
            .strategy(GrayscaleStrategy.valueOf(rs.getString("strategy")))
            .percentage(rs.getObject("percentage") != null ? rs.getInt("percentage") : null)
            .conditionExpr(rs.getString("condition_expr"))
            .status(GrayscaleRuleStatus.valueOf(rs.getString("status")))
            .description(rs.getString("description"))
            .createdBy(rs.getString("created_by"))
            .createdAt(rs.getLong("created_at"))
            .updatedAt(rs.getLong("updated_at"))
            .build();

    public Long insert(GrayscaleRule rule) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_grayscale_rule (project,package_id,approval_id,snapshot_id,strategy,percentage,condition_expr,status,description,created_by,created_at,updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setString(1, rule.getProject());
            ps.setString(2, rule.getPackageId());
            ps.setLong(3, rule.getApprovalId());
            ps.setLong(4, rule.getSnapshotId());
            ps.setString(5, rule.getStrategy().name());
            ps.setObject(6, rule.getPercentage());
            ps.setString(7, rule.getConditionExpr());
            ps.setString(8, rule.getStatus().name());
            ps.setString(9, rule.getDescription());
            ps.setString(10, rule.getCreatedBy());
            ps.setLong(11, rule.getCreatedAt());
            ps.setLong(12, rule.getUpdatedAt());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public GrayscaleRule findById(Long id) {
        List<GrayscaleRule> list = jdbc.query("SELECT * FROM ruleuler_grayscale_rule WHERE id=?", RULE_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public GrayscaleRule findActive(String project, String packageId) {
        List<GrayscaleRule> list = jdbc.query(
                "SELECT * FROM ruleuler_grayscale_rule WHERE project=? AND package_id=? AND status='ACTIVE'",
                RULE_MAPPER, project, packageId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<GrayscaleRule> findActiveByProject(String project) {
        return jdbc.query("SELECT * FROM ruleuler_grayscale_rule WHERE project=? AND status='ACTIVE' ORDER BY created_at DESC",
                RULE_MAPPER, project);
    }

    public List<GrayscaleRule> findAllActive() {
        return jdbc.query("SELECT * FROM ruleuler_grayscale_rule WHERE status='ACTIVE' ORDER BY created_at DESC",
                RULE_MAPPER);
    }

    public List<GrayscaleRule> listByFilter(String project, String packageId, String status, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ruleuler_grayscale_rule WHERE 1=1");
        Object[] params = buildParams(sql, project, packageId, status);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        Object[] full = appendParams(params, limit, offset);
        return jdbc.query(sql.toString(), RULE_MAPPER, full);
    }

    public int countByFilter(String project, String packageId, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ruleuler_grayscale_rule WHERE 1=1");
        Object[] params = buildParams(sql, project, packageId, status);
        Integer cnt = jdbc.queryForObject(sql.toString(), Integer.class, params);
        return cnt != null ? cnt : 0;
    }

    public void updateStatus(Long id, GrayscaleRuleStatus status) {
        jdbc.update("UPDATE ruleuler_grayscale_rule SET status=?, updated_at=? WHERE id=?",
                status.name(), System.currentTimeMillis(), id);
    }

    public void updatePercentage(Long id, int percentage) {
        jdbc.update("UPDATE ruleuler_grayscale_rule SET percentage=?, updated_at=? WHERE id=?",
                percentage, System.currentTimeMillis(), id);
    }

    public void updateCondition(Long id, String conditionExpr) {
        jdbc.update("UPDATE ruleuler_grayscale_rule SET condition_expr=?, updated_at=? WHERE id=?",
                conditionExpr, System.currentTimeMillis(), id);
    }

    // ---- metrics ----

    public void upsertMetrics(Long ruleId, String version, boolean success, long execMs) {
        long now = System.currentTimeMillis();
        java.time.LocalDate today = java.time.LocalDate.now();
        // 先尝试更新，再插入
        int updated = jdbc.update(
                "UPDATE ruleuler_grayscale_metrics SET hit_count=hit_count+1, " +
                (success ? "success_count=success_count+1" : "fail_count=fail_count+1") +
                ", total_exec_ms=total_exec_ms+?, updated_at=? " +
                "WHERE rule_id=? AND version=? AND stat_date=?",
                execMs, now, ruleId, version, today);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO ruleuler_grayscale_metrics (rule_id,version,hit_count,success_count,fail_count,total_exec_ms,stat_date,updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?)",
                    ruleId, version, 1, success ? 1 : 0, success ? 0 : 1, execMs, today, now);
        }
    }

    public List<Map<String, Object>> findMetrics(Long ruleId, String startDate, String endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT version, stat_date, hit_count, success_count, fail_count, total_exec_ms " +
                "FROM ruleuler_grayscale_metrics WHERE rule_id=?");
        if (startDate != null) sql.append(" AND stat_date >= '").append(startDate).append("'");
        if (endDate != null) sql.append(" AND stat_date <= '").append(endDate).append("'");
        sql.append(" ORDER BY stat_date, version");
        return jdbc.queryForList(sql.toString(), ruleId);
    }

    // ---- helpers ----

    private Object[] buildParams(StringBuilder sql, String project, String packageId, String status) {
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (project != null && !project.isEmpty()) { sql.append(" AND project=?"); params.add(project); }
        if (packageId != null && !packageId.isEmpty()) { sql.append(" AND package_id=?"); params.add(packageId); }
        if (status != null && !status.isEmpty()) { sql.append(" AND status=?"); params.add(status); }
        return params.toArray();
    }

    private Object[] appendParams(Object[] base, Object... extra) {
        Object[] result = new Object[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }
}
