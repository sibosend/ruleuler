package com.caritasem.ruleuler.server.audit;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @RequirePermission("menu:system:audit")
    public ApiResult list(@RequestParam(required = false) String action,
                          @RequestParam(required = false) String targetType,
                          @RequestParam(required = false) String operator,
                          @RequestParam(required = false) String project,
                          @RequestParam(required = false) Long startTime,
                          @RequestParam(required = false) Long endTime,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(auditLogService.query(action, targetType, operator, project, startTime, endTime, page, pageSize));
    }
}
