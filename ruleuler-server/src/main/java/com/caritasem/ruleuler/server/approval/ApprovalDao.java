package com.caritasem.ruleuler.server.approval;

import com.caritasem.ruleuler.server.approval.model.*;
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
public class ApprovalDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Approval> APPROVAL_MAPPER = (rs, rowNum) -> Approval.builder()
            .id(rs.getLong("id"))
            .project(rs.getString("project"))
            .packageId(rs.getString("package_id"))
            .packageName(rs.getString("package_name"))
            .status(ApprovalStatus.valueOf(rs.getString("status")))
            .submitter(rs.getString("submitter"))
            .approver(rs.getString("approver"))
            .comment(rs.getString("comment"))
            .failReason(rs.getString("fail_reason"))
            .publisher(rs.getString("publisher"))
            .submittedAt(rs.getLong("submitted_at"))
            .approvedAt(rs.getObject("approved_at") != null ? rs.getLong("approved_at") : null)
            .publishedAt(rs.getObject("published_at") != null ? rs.getLong("published_at") : null)
            .build();

    private static final RowMapper<ApprovalDiffItem> DIFF_MAPPER = (rs, rowNum) -> ApprovalDiffItem.builder()
            .id(rs.getLong("id"))
            .approvalId(rs.getLong("approval_id"))
            .componentPath(rs.getString("component_path"))
            .componentName(rs.getString("component_name"))
            .componentType(rs.getString("component_type"))
            .changeType(rs.getString("change_type"))
            .prevVersion(rs.getString("prev_version"))
            .currVersion(rs.getString("curr_version"))
            .build();

    private static final RowMapper<PublishSnapshot> SNAPSHOT_MAPPER = (rs, rowNum) -> PublishSnapshot.builder()
            .id(rs.getLong("id"))
            .project(rs.getString("project"))
            .packageId(rs.getString("package_id"))
            .approvalId(rs.getObject("approval_id") != null ? rs.getLong("approval_id") : null)
            .snapshotData(rs.getString("snapshot_data"))
            .createdAt(rs.getLong("created_at"))
            .build();

    // ---- Approval ----

    public Long insertApproval(Approval a) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_publish_approval (project,package_id,package_name,status,submitter,comment,submitted_at) " +
                    "VALUES (?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setString(1, a.getProject());
            ps.setString(2, a.getPackageId());
            ps.setString(3, a.getPackageName());
            ps.setString(4, a.getStatus().name());
            ps.setString(5, a.getSubmitter());
            ps.setString(6, a.getComment());
            ps.setLong(7, a.getSubmittedAt());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public Approval findById(Long id) {
        List<Approval> list = jdbc.query("SELECT * FROM ruleuler_publish_approval WHERE id=?", APPROVAL_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean existsPending(String project, String packageId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_publish_approval WHERE project=? AND package_id=? AND status='PENDING'",
                Integer.class, project, packageId);
        return cnt != null && cnt > 0;
    }

    public List<Approval> listByFilter(String project, String packageId, String status,
                                        String submitter, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ruleuler_publish_approval WHERE 1=1");
        Object[] params = buildFilterParams(sql, project, packageId, status, submitter);
        sql.append(" ORDER BY submitted_at DESC LIMIT ? OFFSET ?");
        Object[] full = appendParams(params, limit, offset);
        return jdbc.query(sql.toString(), APPROVAL_MAPPER, full);
    }

    public int countByFilter(String project, String packageId, String status, String submitter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ruleuler_publish_approval WHERE 1=1");
        Object[] params = buildFilterParams(sql, project, packageId, status, submitter);
        Integer cnt = jdbc.queryForObject(sql.toString(), Integer.class, params);
        return cnt != null ? cnt : 0;
    }

    public void updateStatus(Long id, ApprovalStatus status, String approver, String comment, Long approvedAt) {
        jdbc.update("UPDATE ruleuler_publish_approval SET status=?,approver=?,comment=?,approved_at=? WHERE id=?",
                status.name(), approver, comment, approvedAt, id);
    }

    public void updatePublishFailed(Long id, String failReason) {
        jdbc.update("UPDATE ruleuler_publish_approval SET status='PUBLISH_FAILED',fail_reason=? WHERE id=?",
                failReason, id);
    }

    public void updatePublished(Long id, String publisher, long publishedAt) {
        jdbc.update("UPDATE ruleuler_publish_approval SET status='PUBLISHED',publisher=?,published_at=?,fail_reason=NULL WHERE id=?",
                publisher, publishedAt, id);
    }

    // ---- Diff Items ----

    public void batchInsertDiffItems(List<ApprovalDiffItem> items) {
        for (ApprovalDiffItem d : items) {
            jdbc.update(
                    "INSERT INTO ruleuler_publish_approval_diff (approval_id,component_path,component_name,component_type,change_type,prev_version,curr_version) " +
                    "VALUES (?,?,?,?,?,?,?)",
                    d.getApprovalId(), d.getComponentPath(), d.getComponentName(),
                    d.getComponentType(), d.getChangeType(), d.getPrevVersion(), d.getCurrVersion());
        }
    }

    public List<ApprovalDiffItem> findDiffItemsByApprovalId(Long approvalId) {
        return jdbc.query("SELECT * FROM ruleuler_publish_approval_diff WHERE approval_id=? ORDER BY id",
                DIFF_MAPPER, approvalId);
    }

    // ---- Snapshot ----

    public Long insertSnapshot(PublishSnapshot s) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ruleuler_publish_snapshot (project,package_id,approval_id,snapshot_data,created_at) " +
                    "VALUES (?,?,?,?,?)", new String[]{"id"});
            ps.setString(1, s.getProject());
            ps.setString(2, s.getPackageId());
            ps.setObject(3, s.getApprovalId());
            ps.setString(4, s.getSnapshotData());
            ps.setLong(5, s.getCreatedAt());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public PublishSnapshot findLatestSnapshot(String project, String packageId) {
        List<PublishSnapshot> list = jdbc.query(
                "SELECT * FROM ruleuler_publish_snapshot WHERE project=? AND package_id=? ORDER BY created_at DESC LIMIT 1",
                SNAPSHOT_MAPPER, project, packageId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---- helpers ----

    private Object[] buildFilterParams(StringBuilder sql, String project, String packageId,
                                        String status, String submitter) {
        List<Object> params = new ArrayList<>();
        if (project != null && !project.isEmpty()) { sql.append(" AND project=?"); params.add(project); }
        if (packageId != null && !packageId.isEmpty()) { sql.append(" AND package_id=?"); params.add(packageId); }
        if (status != null && !status.isEmpty()) {
            if (status.contains(",")) {
                String[] parts = status.split(",");
                sql.append(" AND status IN (");
                for (int i = 0; i < parts.length; i++) {
                    sql.append(i > 0 ? ",?" : "?");
                    params.add(parts[i].trim());
                }
                sql.append(")");
            } else {
                sql.append(" AND status=?");
                params.add(status);
            }
        }
        if (submitter != null && !submitter.isEmpty()) { sql.append(" AND submitter=?"); params.add(submitter); }
        return params.toArray();
    }

    private Object[] appendParams(Object[] base, Object... extra) {
        Object[] result = new Object[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }
}
