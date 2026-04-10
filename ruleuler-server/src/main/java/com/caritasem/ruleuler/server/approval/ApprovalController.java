package com.caritasem.ruleuler.server.approval;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @RequirePermission("pack:publish:submit")
    public ApiResult submit(@RequestBody Map<String, String> body) {
        String project = body.get("project");
        String packageId = body.get("packageId");
        if (project == null || packageId == null) {
            return ApiResult.error(400, "project 和 packageId 不能为空");
        }
        AuthContext.UserInfo user = AuthContext.get();
        return ApiResult.ok(approvalService.submit(project, packageId, user.getUsername()));
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
        return ApiResult.ok(approvalService.approve(id, user.getUsername(), comment));
    }

    @PutMapping("/{id}/reject")
    @RequirePermission("pack:publish:approve")
    public ApiResult reject(@PathVariable Long id,
                             @RequestBody(required = false) Map<String, String> body) {
        AuthContext.UserInfo user = AuthContext.get();
        String comment = body != null ? body.get("comment") : null;
        return ApiResult.ok(approvalService.reject(id, user.getUsername(), comment));
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("pack:publish:submit")
    public ApiResult publish(@PathVariable Long id) {
        String publisher = AuthContext.get().getUsername();
        return ApiResult.ok(approvalService.publish(id, publisher));
    }
}
