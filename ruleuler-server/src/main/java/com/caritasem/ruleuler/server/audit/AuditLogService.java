package com.caritasem.ruleuler.server.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入审计日志。detail 对象会被序列化为 JSON。
     */
    public void log(String action, String targetType, Long targetId, String targetPath,
                    String project, String operator, Object detail, String ip) {
        try {
            String detailJson = detail != null ? objectMapper.writeValueAsString(detail) : null;
            jdbcTemplate.update(
                    "INSERT INTO ruleuler_audit_log(action, target_type, target_id, target_path, project, operator, detail, ip, created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    action, targetType, targetId, targetPath, project, operator, detailJson, ip, System.currentTimeMillis());
        } catch (Exception e) {
            // 审计日志写入失败不应阻断业务流程
            System.err.println("[AuditLog] write failed: " + e.getMessage());
        }
    }

    /**
     * 分页查询审计日志
     */
    public Map<String, Object> query(String action, String targetType, String operator,
                                     String project, Long startTime, Long endTime,
                                     int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (action != null && !action.isBlank()) {
            where.append(" AND action=?");
            params.add(action);
        }
        if (targetType != null && !targetType.isBlank()) {
            where.append(" AND target_type=?");
            params.add(targetType);
        }
        if (operator != null && !operator.isBlank()) {
            where.append(" AND operator=?");
            params.add(operator);
        }
        if (project != null && !project.isBlank()) {
            where.append(" AND project=?");
            params.add(project);
        }
        if (startTime != null) {
            where.append(" AND created_at>=?");
            params.add(startTime);
        }
        if (endTime != null) {
            where.append(" AND created_at<=?");
            params.add(endTime);
        }

        String countSql = "SELECT COUNT(*) FROM ruleuler_audit_log" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        where.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        String dataSql = "SELECT id, action, target_type, target_id, target_path, project, operator, detail, ip, created_at FROM ruleuler_audit_log" + where;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, params.toArray());

        return Map.of("records", records, "total", total);
    }
}
