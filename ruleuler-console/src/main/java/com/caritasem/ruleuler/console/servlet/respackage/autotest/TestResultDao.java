package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

public class TestResultDao {

    private JdbcTemplate jdbcTemplate;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- TestCasePack CRUD ----

    public long createPack(TestCasePack pack) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_test_case_pack (project, package_id, pack_name, source_type, total_cases, created_at) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, pack.getProject());
            ps.setString(2, pack.getPackageId());
            ps.setString(3, pack.getPackName());
            ps.setString(4, pack.getSourceType());
            ps.setInt(5, pack.getTotalCases());
            ps.setLong(6, pack.getCreatedAt());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public List<TestCasePack> findPacksByPackage(String project, String packageId) {
        if (packageId == null || packageId.isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT * FROM ruleuler_test_case_pack WHERE project=? ORDER BY id DESC",
                    TEST_CASE_PACK_ROW_MAPPER, project);
        }
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_case_pack WHERE project=? AND package_id=? ORDER BY id DESC",
                TEST_CASE_PACK_ROW_MAPPER, project, packageId);
    }

    public TestCasePack findPackById(long packId) {
        List<TestCasePack> list = jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_case_pack WHERE id=?",
                TEST_CASE_PACK_ROW_MAPPER, packId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---- TestCase CRUD ----

    public void batchInsertCases(List<TestCase> cases) {
        String sql = "INSERT INTO ruleuler_test_case (pack_id, project, package_id, flow_file, case_name, path_description, input_data, expected_type, flipped_condition, test_purpose, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TestCase c = cases.get(i);
                ps.setLong(1, c.getPackId());
                ps.setString(2, c.getProject());
                ps.setString(3, c.getPackageId());
                ps.setString(4, c.getFlowFile());
                ps.setString(5, c.getCaseName());
                ps.setString(6, c.getPathDescription());
                ps.setString(7, c.getInputData());
                ps.setString(8, c.getExpectedType());
                ps.setString(9, c.getFlippedCondition());
                ps.setString(10, c.getTestPurpose());
                ps.setLong(11, c.getCreatedAt());
                ps.setLong(12, c.getUpdatedAt());
            }

            @Override
            public int getBatchSize() {
                return cases.size();
            }
        });
        // 回填自增 ID（用于 TestResult 关联）
        if (!cases.isEmpty()) {
            long maxId = jdbcTemplate.queryForObject(
                    "SELECT MAX(id) FROM ruleuler_test_case WHERE pack_id=?", Long.class, cases.get(0).getPackId());
            long startId = maxId - cases.size() + 1;
            for (int i = 0; i < cases.size(); i++) {
                cases.get(i).setId(startId + i);
            }
        }
    }

    public List<TestCase> findCasesByPackId(long packId) {
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_case WHERE pack_id=? ORDER BY id",
                TEST_CASE_ROW_MAPPER, packId);
    }

    public int countCasesByPackId(long packId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_test_case WHERE pack_id=?", Integer.class, packId);
        return count == null ? 0 : count;
    }

    // ---- TestRun CRUD ----

    public long createRun(TestRun run) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_test_run (project, package_id, pack_id, run_type, baseline_run_id, total_cases, executed_cases, passed_cases, failed_cases, status, started_at, finished_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, run.getProject());
            ps.setString(2, run.getPackageId());
            ps.setLong(3, run.getPackId());
            ps.setString(4, run.getRunType());
            if (run.getBaselineRunId() != null) {
                ps.setLong(5, run.getBaselineRunId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setInt(6, run.getTotalCases());
            ps.setInt(7, run.getExecutedCases());
            ps.setInt(8, run.getPassedCases());
            ps.setInt(9, run.getFailedCases());
            ps.setString(10, run.getStatus());
            ps.setLong(11, run.getStartedAt());
            if (run.getFinishedAt() != null) {
                ps.setLong(12, run.getFinishedAt());
            } else {
                ps.setNull(12, Types.BIGINT);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateRun(TestRun run) {
        jdbcTemplate.update(
                "UPDATE ruleuler_test_run SET total_cases=?, passed_cases=?, failed_cases=?, status=?, finished_at=? WHERE id=?",
                run.getTotalCases(), run.getPassedCases(), run.getFailedCases(),
                run.getStatus(), run.getFinishedAt(), run.getId());
    }

    public void updateRunProgress(long runId, int executedCases) {
        jdbcTemplate.update(
                "UPDATE ruleuler_test_run SET executed_cases=? WHERE id=?",
                executedCases, runId);
    }

    public TestRun findRunById(long runId) {
        List<TestRun> list = jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_run WHERE id=?",
                TEST_RUN_ROW_MAPPER, runId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TestRun> findRunsByPackage(String project, String packageId) {
        if (packageId == null || packageId.isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT * FROM ruleuler_test_run WHERE project=? ORDER BY id DESC",
                    TEST_RUN_ROW_MAPPER, project);
        }
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_run WHERE project=? AND package_id=? ORDER BY id DESC",
                TEST_RUN_ROW_MAPPER, project, packageId);
    }

    // ---- TestResult CRUD ----

    public void batchInsertResults(List<TestResult> results) {
        String sql = "INSERT INTO ruleuler_test_result (run_id, case_id, passed, actual_output, execution_time_ms, error_message, baseline_output, diff_status, created_at) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TestResult r = results.get(i);
                ps.setLong(1, r.getRunId());
                ps.setLong(2, r.getCaseId());
                ps.setBoolean(3, r.isPassed());
                ps.setString(4, r.getActualOutput());
                if (r.getExecutionTimeMs() != null) {
                    ps.setLong(5, r.getExecutionTimeMs());
                } else {
                    ps.setNull(5, Types.BIGINT);
                }
                ps.setString(6, r.getErrorMessage());
                ps.setString(7, r.getBaselineOutput());
                ps.setString(8, r.getDiffStatus());
                ps.setLong(9, r.getCreatedAt());
            }

            @Override
            public int getBatchSize() {
                return results.size();
            }
        });
    }

    public List<TestResult> findResultsByRunId(long runId) {
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_result WHERE run_id=? ORDER BY id",
                TEST_RESULT_ROW_MAPPER, runId);
    }

    /**
     * 将指定 run 的所有结果设置为指定 diff_status。
     */
    public void setAllDiffStatus(long runId, String status) {
        jdbcTemplate.update("UPDATE ruleuler_test_result SET diff_status=? WHERE run_id=?", status, runId);
    }

    /**
     * 批量比对 diff_status：将当前 run 的每条结果与 baseline run 的同 case_id 结果比对。
     * actual_output 相同 → SAME，不同 → CHANGED（同时填充 baseline_output）。
     */
    public void batchUpdateDiffStatus(long runId, long baselineRunId) {
        // 标记 SAME：actual_output 与 baseline 一致
        jdbcTemplate.update(
                "UPDATE ruleuler_test_result r " +
                "INNER JOIN ruleuler_test_result b ON r.case_id = b.case_id AND b.run_id = ? " +
                "SET r.diff_status = 'SAME', r.baseline_output = b.actual_output " +
                "WHERE r.run_id = ? AND r.actual_output = b.actual_output",
                baselineRunId, runId);
        // 标记 CHANGED：actual_output 与 baseline 不一致（包括 null 不等）
        jdbcTemplate.update(
                "UPDATE ruleuler_test_result r " +
                "INNER JOIN ruleuler_test_result b ON r.case_id = b.case_id AND b.run_id = ? " +
                "SET r.diff_status = 'CHANGED', r.baseline_output = b.actual_output " +
                "WHERE r.run_id = ? AND (r.actual_output != b.actual_output OR " +
                "(r.actual_output IS NULL AND b.actual_output IS NOT NULL) OR " +
                "(r.actual_output IS NOT NULL AND b.actual_output IS NULL))",
                baselineRunId, runId);
    }

    /** 用例+结果 join 查询，带分页 */
    public List<java.util.Map<String, Object>> findCaseWithResults(long packId, long runId, int page, int size) {
        int offset = (page - 1) * size;
        return jdbcTemplate.queryForList(
                "SELECT c.id as case_id, c.case_name, c.path_description, c.expected_type, c.flipped_condition, c.test_purpose, c.input_data, " +
                "r.passed, r.actual_output, r.execution_time_ms, r.error_message, r.baseline_output, r.diff_status " +
                "FROM ruleuler_test_case c LEFT JOIN ruleuler_test_result r ON c.id = r.case_id AND r.run_id=? " +
                "WHERE c.pack_id=? ORDER BY c.id LIMIT ? OFFSET ?",
                runId, packId, size, offset);
    }

    // ---- TestSegment CRUD ----

    public void batchInsertSegments(List<TestSegment> segments) {
        String sql = "INSERT INTO ruleuler_test_segment (run_id, variable_name, variable_type, segment_label, case_count, percentage, baseline_count, baseline_percentage, change_pct) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TestSegment s = segments.get(i);
                ps.setLong(1, s.getRunId());
                ps.setString(2, s.getVariableName());
                ps.setString(3, s.getVariableType());
                ps.setString(4, s.getSegmentLabel());
                ps.setInt(5, s.getCaseCount());
                ps.setBigDecimal(6, s.getPercentage());
                if (s.getBaselineCount() != null) {
                    ps.setInt(7, s.getBaselineCount());
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                if (s.getBaselinePercentage() != null) {
                    ps.setBigDecimal(8, s.getBaselinePercentage());
                } else {
                    ps.setNull(8, Types.DECIMAL);
                }
                if (s.getChangePct() != null) {
                    ps.setBigDecimal(9, s.getChangePct());
                } else {
                    ps.setNull(9, Types.DECIMAL);
                }
            }

            @Override
            public int getBatchSize() {
                return segments.size();
            }
        });
    }

    public List<TestSegment> findSegmentsByRunId(long runId) {
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_segment WHERE run_id=? ORDER BY id",
                TEST_SEGMENT_ROW_MAPPER, runId);
    }

    // ---- ConflictItem CRUD ----

    public void batchInsertConflicts(List<ConflictItem> conflicts) {
        String sql = "INSERT INTO ruleuler_test_conflict (run_id, conflict_type, severity, rule_file, location, description, created_at) VALUES (?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ConflictItem c = conflicts.get(i);
                ps.setLong(1, c.getRunId());
                ps.setString(2, c.getConflictType());
                ps.setString(3, c.getSeverity());
                ps.setString(4, c.getRuleFile());
                ps.setString(5, c.getLocation());
                ps.setString(6, c.getDescription());
                ps.setLong(7, System.currentTimeMillis());
            }

            @Override
            public int getBatchSize() {
                return conflicts.size();
            }
        });
    }

    public List<ConflictItem> findConflictsByRunId(long runId) {
        return jdbcTemplate.query(
                "SELECT * FROM ruleuler_test_conflict WHERE run_id=? ORDER BY id",
                CONFLICT_ITEM_ROW_MAPPER, runId);
    }

    // ---- RowMappers ----

    private static final RowMapper<TestCasePack> TEST_CASE_PACK_ROW_MAPPER = (rs, rowNum) -> {
        TestCasePack p = new TestCasePack();
        p.setId(rs.getLong("id"));
        p.setProject(rs.getString("project"));
        p.setPackageId(rs.getString("package_id"));
        p.setPackName(rs.getString("pack_name"));
        p.setSourceType(rs.getString("source_type"));
        p.setTotalCases(rs.getInt("total_cases"));
        p.setCreatedAt(rs.getLong("created_at"));
        return p;
    };

    private static final RowMapper<TestCase> TEST_CASE_ROW_MAPPER = (rs, rowNum) -> {
        TestCase c = new TestCase();
        c.setId(rs.getLong("id"));
        c.setPackId(rs.getLong("pack_id"));
        c.setProject(rs.getString("project"));
        c.setPackageId(rs.getString("package_id"));
        c.setFlowFile(rs.getString("flow_file"));
        c.setCaseName(rs.getString("case_name"));
        c.setPathDescription(rs.getString("path_description"));
        c.setInputData(rs.getString("input_data"));
        c.setExpectedType(rs.getString("expected_type"));
        c.setFlippedCondition(rs.getString("flipped_condition"));
        c.setTestPurpose(rs.getString("test_purpose"));
        c.setCreatedAt(rs.getLong("created_at"));
        c.setUpdatedAt(rs.getLong("updated_at"));
        return c;
    };

    private static final RowMapper<TestRun> TEST_RUN_ROW_MAPPER = (rs, rowNum) -> {
        TestRun r = new TestRun();
        r.setId(rs.getLong("id"));
        r.setProject(rs.getString("project"));
        r.setPackageId(rs.getString("package_id"));
        r.setPackId(rs.getLong("pack_id"));
        r.setRunType(rs.getString("run_type"));
        long baselineRunId = rs.getLong("baseline_run_id");
        r.setBaselineRunId(rs.wasNull() ? null : baselineRunId);
        r.setExecutedCases(rs.getInt("executed_cases"));
        r.setTotalCases(rs.getInt("total_cases"));
        r.setPassedCases(rs.getInt("passed_cases"));
        r.setFailedCases(rs.getInt("failed_cases"));
        r.setStatus(rs.getString("status"));
        r.setStartedAt(rs.getLong("started_at"));
        long finishedAt = rs.getLong("finished_at");
        r.setFinishedAt(rs.wasNull() ? null : finishedAt);
        return r;
    };

    private static final RowMapper<TestResult> TEST_RESULT_ROW_MAPPER = (rs, rowNum) -> {
        TestResult r = new TestResult();
        r.setId(rs.getLong("id"));
        r.setRunId(rs.getLong("run_id"));
        r.setCaseId(rs.getLong("case_id"));
        r.setPassed(rs.getBoolean("passed"));
        r.setActualOutput(rs.getString("actual_output"));
        long execTime = rs.getLong("execution_time_ms");
        r.setExecutionTimeMs(rs.wasNull() ? null : execTime);
        r.setErrorMessage(rs.getString("error_message"));
        r.setBaselineOutput(rs.getString("baseline_output"));
        r.setDiffStatus(rs.getString("diff_status"));
        r.setCreatedAt(rs.getLong("created_at"));
        return r;
    };

    private static final RowMapper<TestSegment> TEST_SEGMENT_ROW_MAPPER = (rs, rowNum) -> {
        TestSegment s = new TestSegment();
        s.setId(rs.getLong("id"));
        s.setRunId(rs.getLong("run_id"));
        s.setVariableName(rs.getString("variable_name"));
        s.setVariableType(rs.getString("variable_type"));
        s.setSegmentLabel(rs.getString("segment_label"));
        s.setCaseCount(rs.getInt("case_count"));
        s.setPercentage(rs.getBigDecimal("percentage"));
        int baselineCount = rs.getInt("baseline_count");
        s.setBaselineCount(rs.wasNull() ? null : baselineCount);
        s.setBaselinePercentage(rs.getBigDecimal("baseline_percentage"));
        s.setChangePct(rs.getBigDecimal("change_pct"));
        return s;
    };

    private static final RowMapper<ConflictItem> CONFLICT_ITEM_ROW_MAPPER = (rs, rowNum) -> {
        ConflictItem c = new ConflictItem();
        c.setRunId(rs.getLong("run_id"));
        c.setConflictType(rs.getString("conflict_type"));
        c.setSeverity(rs.getString("severity"));
        c.setRuleFile(rs.getString("rule_file"));
        c.setLocation(rs.getString("location"));
        c.setDescription(rs.getString("description"));
        return c;
    };
}
