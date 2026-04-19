package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.server.replay.model.ReplaySession;
import com.caritasem.ruleuler.server.replay.model.ReplayTask;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReplayDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<ReplayTask> TASK_MAPPER = (rs, rowNum) -> ReplayTask.builder()
            .id(rs.getLong("id"))
            .project(rs.getString("project"))
            .packageId(rs.getString("package_id"))
            .flowId(rs.getString("flow_id"))
            .trafficQuery(rs.getString("traffic_query"))
            .sampleStrategy(rs.getString("sample_strategy"))
            .sampleSize(rs.getInt("sample_size"))
            .missingVarStrategy(rs.getString("missing_var_strategy"))
            .totalCount(rs.getInt("total_count"))
            .executedCount(rs.getInt("executed_count"))
            .matchCount(rs.getInt("match_count"))
            .mismatchCount(rs.getInt("mismatch_count"))
            .errorCount(rs.getInt("error_count"))
            .incompleteCount(rs.getInt("incomplete_count"))
            .status(rs.getString("status"))
            .toleranceConfig(rs.getString("tolerance_config"))
            .startedAt(rs.getObject("started_at") != null ? rs.getLong("started_at") : null)
            .finishedAt(rs.getObject("finished_at") != null ? rs.getLong("finished_at") : null)
            .createdAt(rs.getLong("created_at"))
            .build();

    private static final RowMapper<ReplaySession> SESSION_MAPPER = (rs, rowNum) -> ReplaySession.builder()
            .id(rs.getLong("id"))
            .taskId(rs.getLong("task_id"))
            .originalExecutionId(rs.getString("original_execution_id"))
            .replayInput(rs.getString("replay_input"))
            .originalOutput(rs.getString("original_output"))
            .replayOutput(rs.getString("replay_output"))
            .diffResult(rs.getString("diff_result"))
            .missingCategories(rs.getString("missing_categories"))
            .missingVariables(rs.getString("missing_variables"))
            .filledVariables(rs.getString("filled_variables"))
            .completenessStatus(rs.getString("completeness_status"))
            .execMs(rs.getObject("exec_ms") != null ? rs.getInt("exec_ms") : null)
            .originalExecMs(rs.getObject("original_exec_ms") != null ? rs.getInt("original_exec_ms") : null)
            .status(rs.getString("status"))
            .errorMessage(rs.getString("error_message"))
            .createdAt(rs.getLong("created_at"))
            .build();

    public Long insertTask(ReplayTask task) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_replay_task (project,package_id,flow_id,traffic_query,sample_strategy," +
                    "sample_size,missing_var_strategy,total_count,executed_count,match_count,mismatch_count," +
                    "error_count,incomplete_count,status,tolerance_config,started_at,finished_at,created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setString(1, task.getProject());
            ps.setString(2, task.getPackageId());
            ps.setString(3, task.getFlowId());
            ps.setString(4, task.getTrafficQuery());
            ps.setString(5, task.getSampleStrategy());
            ps.setInt(6, task.getSampleSize());
            ps.setString(7, task.getMissingVarStrategy());
            ps.setInt(8, task.getTotalCount());
            ps.setInt(9, task.getExecutedCount());
            ps.setInt(10, task.getMatchCount());
            ps.setInt(11, task.getMismatchCount());
            ps.setInt(12, task.getErrorCount());
            ps.setInt(13, task.getIncompleteCount());
            ps.setString(14, task.getStatus());
            ps.setString(15, task.getToleranceConfig());
            ps.setObject(16, task.getStartedAt());
            ps.setObject(17, task.getFinishedAt());
            ps.setLong(18, task.getCreatedAt());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public ReplayTask findTaskById(Long id) {
        List<ReplayTask> list = jdbc.query("SELECT * FROM ruleuler_replay_task WHERE id=?", TASK_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<ReplayTask> listTasks(String project, String packageId, String status, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ruleuler_replay_task WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (project != null && !project.isEmpty()) { sql.append(" AND project=?"); params.add(project); }
        if (packageId != null && !packageId.isEmpty()) { sql.append(" AND package_id=?"); params.add(packageId); }
        if (status != null && !status.isEmpty()) { sql.append(" AND status=?"); params.add(status); }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbc.query(sql.toString(), TASK_MAPPER, params.toArray());
    }

    public int countTasks(String project, String packageId, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ruleuler_replay_task WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (project != null && !project.isEmpty()) { sql.append(" AND project=?"); params.add(project); }
        if (packageId != null && !packageId.isEmpty()) { sql.append(" AND package_id=?"); params.add(packageId); }
        if (status != null && !status.isEmpty()) { sql.append(" AND status=?"); params.add(status); }
        Integer cnt = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return cnt != null ? cnt : 0;
    }

    public void updateTaskStatus(Long id, String status) {
        jdbc.update("UPDATE ruleuler_replay_task SET status=? WHERE id=?", status, id);
    }

    public void updateTaskRunning(Long id) {
        jdbc.update("UPDATE ruleuler_replay_task SET status='running', started_at=? WHERE id=?",
                System.currentTimeMillis(), id);
    }

    public void updateTaskProgress(Long id, int executedCount, int matchCount, int mismatchCount,
                                    int errorCount, int incompleteCount) {
        jdbc.update("UPDATE ruleuler_replay_task SET executed_count=?, match_count=?, mismatch_count=?," +
                "error_count=?, incomplete_count=? WHERE id=?",
                executedCount, matchCount, mismatchCount, errorCount, incompleteCount, id);
    }

    public void updateTaskFinished(Long id, String status) {
        jdbc.update("UPDATE ruleuler_replay_task SET status=?, finished_at=? WHERE id=?",
                status, System.currentTimeMillis(), id);
    }

    public Long insertSession(ReplaySession session) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_replay_session (task_id,original_execution_id,replay_input," +
                    "original_output,replay_output,diff_result,missing_categories,missing_variables," +
                    "filled_variables,completeness_status,exec_ms,original_exec_ms,status,error_message,created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setLong(1, session.getTaskId());
            ps.setString(2, session.getOriginalExecutionId());
            ps.setString(3, session.getReplayInput());
            ps.setString(4, session.getOriginalOutput());
            ps.setString(5, session.getReplayOutput());
            ps.setString(6, session.getDiffResult());
            ps.setString(7, session.getMissingCategories());
            ps.setString(8, session.getMissingVariables());
            ps.setString(9, session.getFilledVariables());
            ps.setString(10, session.getCompletenessStatus());
            ps.setObject(11, session.getExecMs());
            ps.setObject(12, session.getOriginalExecMs());
            ps.setString(13, session.getStatus());
            ps.setString(14, session.getErrorMessage());
            ps.setLong(15, session.getCreatedAt());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public void batchInsertSessions(List<ReplaySession> sessions) {
        String sql = "INSERT INTO ruleuler_replay_session (task_id,original_execution_id,replay_input," +
                "original_output,replay_output,diff_result,missing_categories,missing_variables," +
                "filled_variables,completeness_status,exec_ms,original_exec_ms,status,error_message,created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, sessions, sessions.size(), (ps, s) -> {
            ps.setLong(1, s.getTaskId());
            ps.setString(2, s.getOriginalExecutionId());
            ps.setString(3, s.getReplayInput());
            ps.setString(4, s.getOriginalOutput());
            ps.setString(5, s.getReplayOutput());
            ps.setString(6, s.getDiffResult());
            ps.setString(7, s.getMissingCategories());
            ps.setString(8, s.getMissingVariables());
            ps.setString(9, s.getFilledVariables());
            ps.setString(10, s.getCompletenessStatus());
            ps.setObject(11, s.getExecMs());
            ps.setObject(12, s.getOriginalExecMs());
            ps.setString(13, s.getStatus());
            ps.setString(14, s.getErrorMessage());
            ps.setLong(15, s.getCreatedAt());
        });
    }

    public List<ReplaySession> findSessionsByTaskId(Long taskId, String statusFilter,
                                                     Boolean matchFilter, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ruleuler_replay_session WHERE task_id=?");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql.append(" AND status=?");
            params.add(statusFilter);
        }
        if (matchFilter != null) {
            if (matchFilter) {
                sql.append(" AND JSON_EXTRACT(diff_result, '$.match') = true");
            } else {
                sql.append(" AND (JSON_EXTRACT(diff_result, '$.match') = false OR diff_result IS NULL)");
            }
        }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbc.query(sql.toString(), SESSION_MAPPER, params.toArray());
    }

    public int countSessions(Long taskId, String statusFilter, Boolean matchFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ruleuler_replay_session WHERE task_id=?");
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql.append(" AND status=?");
            params.add(statusFilter);
        }
        if (matchFilter != null) {
            if (matchFilter) {
                sql.append(" AND JSON_EXTRACT(diff_result, '$.match') = true");
            } else {
                sql.append(" AND (JSON_EXTRACT(diff_result, '$.match') = false OR diff_result IS NULL)");
            }
        }
        Integer cnt = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return cnt != null ? cnt : 0;
    }

    public List<ReplaySession> findSessionsByTaskIdAll(Long taskId) {
        return jdbc.query("SELECT * FROM ruleuler_replay_session WHERE task_id=? ORDER BY id",
                SESSION_MAPPER, taskId);
    }
}
