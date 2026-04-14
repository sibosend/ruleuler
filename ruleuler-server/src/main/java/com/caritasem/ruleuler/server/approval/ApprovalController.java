package com.caritasem.ruleuler.server.approval;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import com.caritasem.ruleuler.server.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final AuditLogService auditLogService;

    @PostMapping
    @RequirePermission("pack:publish:submit")
    public ApiResult submit(@RequestBody Map<String, String> body) {
        String project = body.get("project");
        String packageId = body.get("packageId");
        String description = body.get("description");
        if (project == null || packageId == null) {
            return ApiResult.error(400, "project 和 packageId 不能为空");
        }
        AuthContext.UserInfo user = AuthContext.get();
        Object result = approvalService.submit(project, packageId, user.getUsername(), description);
        auditLogService.log("PUBLISH_SUBMIT", "APPROVAL", null, null, project, user.getUsername(),
                Map.of("packageId", packageId, "description", description != null ? description : ""), null);
        return ApiResult.ok(result);
    }

    @GetMapping
    public ApiResult list(@RequestParam(required = false) String project,
                          @RequestParam(required = false) String packageId,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String submitter,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(approvalService.list(project, packageId, status, submitter, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResult getDetail(@PathVariable Long id) {
        return ApiResult.ok(approvalService.getDetail(id));
    }

    @PutMapping("/{id}/approve")
    @RequirePermission("pack:publish:approve")
    public ApiResult approve(@PathVariable Long id,
                              @RequestBody(required = false) Map<String, String> body) {
        AuthContext.UserInfo user = AuthContext.get();
        String comment = body != null ? body.get("comment") : null;
        Object result = approvalService.approve(id, user.getUsername(), comment);
        auditLogService.log("APPROVE", "APPROVAL", id, null, null, user.getUsername(),
                Map.of("approvalId", id, "comment", comment != null ? comment : ""), null);
        return ApiResult.ok(result);
    }

    @PutMapping("/{id}/reject")
    @RequirePermission("pack:publish:approve")
    public ApiResult reject(@PathVariable Long id,
                             @RequestBody(required = false) Map<String, String> body) {
        AuthContext.UserInfo user = AuthContext.get();
        String comment = body != null ? body.get("comment") : null;
        Object result = approvalService.reject(id, user.getUsername(), comment);
        auditLogService.log("REJECT", "APPROVAL", id, null, null, user.getUsername(),
                Map.of("approvalId", id, "comment", comment != null ? comment : ""), null);
        return ApiResult.ok(result);
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("pack:publish:submit")
    public ApiResult publish(@PathVariable Long id) {
        String publisher = AuthContext.get().getUsername();
        Object result = approvalService.publish(id, publisher);
        auditLogService.log("PUBLISH", "APPROVAL", id, null, null, publisher,
                Map.of("approvalId", id), null);
        return ApiResult.ok(result);
    }

    @PostMapping("/{id}/recalc-diff")
    @RequirePermission("pack:publish:submit")
    public ApiResult recalcDiff(@PathVariable Long id) {
        return ApiResult.ok(approvalService.recalcDiff(id));
    }
}
