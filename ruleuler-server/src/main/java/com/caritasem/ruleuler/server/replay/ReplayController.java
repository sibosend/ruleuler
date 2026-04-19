package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.replay.model.ToleranceConfig;
import com.caritasem.ruleuler.server.replay.model.TrafficQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/replay")
public class ReplayController {

    @Autowired
    private ReplayService replayService;

    @PostMapping("/tasks")
    public ApiResult createTask(@RequestBody Map<String, Object> body) {
        try {
            TrafficQuery query = TrafficQuery.builder()
                    .project((String) body.get("project"))
                    .packageId((String) body.get("packageId"))
                    .flowId((String) body.get("flowId"))
                    .startTime(body.get("startTime") != null ? ((Number) body.get("startTime")).longValue() : null)
                    .endTime(body.get("endTime") != null ? ((Number) body.get("endTime")).longValue() : null)
                    .sampleStrategy(body.get("sampleStrategy") != null ? (String) body.get("sampleStrategy") : "random")
                    .sampleSize(body.get("sampleSize") != null ? ((Number) body.get("sampleSize")).intValue() : 10000)
                    .missingVarStrategy(body.get("missingVarStrategy") != null ? (String) body.get("missingVarStrategy") : "null")
                    .build();

            if (query.getProject() == null || query.getPackageId() == null) {
                return ApiResult.error(400, "project 和 packageId 不能为空");
            }
            if (query.getSampleSize() > 10000) {
                return ApiResult.error(400, "采样数量不能超过 10000");
            }

            ToleranceConfig tolerance = ToleranceConfig.exact();
            if (body.get("toleranceConfig") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tc = (Map<String, Object>) body.get("toleranceConfig");
                tolerance = new ToleranceConfig(
                        tc.get("mode") != null ? (String) tc.get("mode") : "exact",
                        tc.get("value") != null ? ((Number) tc.get("value")).doubleValue() : 0
                );
            }

            long taskId = replayService.createAndExecute(query, tolerance);
            return ApiResult.ok(Map.of("taskId", taskId));
        } catch (IllegalArgumentException e) {
            return ApiResult.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResult.error(500, e.getMessage());
        }
    }

    @GetMapping("/tasks")
    public ApiResult listTasks(@RequestParam(required = false) String project,
                               @RequestParam(required = false) String packageId,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(replayService.listTasks(project, packageId, status, page, pageSize));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResult getTask(@PathVariable Long taskId) {
        var task = replayService.getTask(taskId);
        if (task == null) return ApiResult.error(404, "任务不存在");
        return ApiResult.ok(task);
    }

    @GetMapping("/tasks/{taskId}/sessions")
    public ApiResult listSessions(@PathVariable Long taskId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Boolean match,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(replayService.listSessions(taskId, status, match, page, pageSize));
    }

    @GetMapping("/tasks/{taskId}/report")
    public ApiResult getReport(@PathVariable Long taskId) {
        try {
            return ApiResult.ok(replayService.getReport(taskId));
        } catch (IllegalArgumentException e) {
            return ApiResult.error(404, e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/export")
    public ApiResult exportTask(@PathVariable Long taskId, @RequestBody Map<String, String> body) {
        try {
            String scope = body.getOrDefault("scope", "all");
            long packId = replayService.exportToTestCasePack(taskId, scope);
            return ApiResult.ok(Map.of("packId", packId));
        } catch (IllegalArgumentException e) {
            return ApiResult.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResult.error(500, e.getMessage());
        }
    }
}
