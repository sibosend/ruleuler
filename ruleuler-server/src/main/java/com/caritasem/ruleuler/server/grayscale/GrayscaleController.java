package com.caritasem.ruleuler.server.grayscale;

import com.caritasem.ruleuler.server.grayscale.model.GrayscaleStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/grayscale")
@RequiredArgsConstructor
public class GrayscaleController {

    private final GrayscaleService grayscaleService;

    @PostMapping("/rules")
    public Map<String, Object> createRule(@RequestBody Map<String, Object> body) {
        Long approvalId = Long.valueOf(body.get("approvalId").toString());
        GrayscaleStrategy strategy = GrayscaleStrategy.valueOf(body.get("strategy").toString());
        Integer percentage = body.get("percentage") != null ? Integer.valueOf(body.get("percentage").toString()) : null;
        String conditionExpr = (String) body.get("conditionExpr");
        String description = (String) body.get("description");
        String operator = (String) body.get("operator");
        return grayscaleService.createRule(approvalId, strategy, percentage, conditionExpr, description, operator);
    }

    @GetMapping("/rules")
    public Map<String, Object> listRules(@RequestParam(required = false) String project,
                                          @RequestParam(required = false) String packageId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return grayscaleService.listRules(project, packageId, status, page, pageSize);
    }

    @GetMapping("/rules/{id}")
    public Map<String, Object> getDetail(@PathVariable Long id) {
        return grayscaleService.getDetail(id);
    }

    @PutMapping("/rules/{id}/rollout")
    public Map<String, Object> fullRollout(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String operator = (String) body.get("operator");
        return grayscaleService.fullRollout(id, operator);
    }

    @PutMapping("/rules/{id}/rollback")
    public Map<String, Object> rollback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String operator = (String) body.get("operator");
        return grayscaleService.rollback(id, operator);
    }

    @GetMapping("/rules/{id}/metrics")
    public Map<String, Object> getMetrics(@PathVariable Long id,
                                           @RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate) {
        return grayscaleService.getMetrics(id, startDate, endDate);
    }

    /** Client 启动恢复用：返回项目所有活跃灰度状态 */
    @GetMapping("/active-states")
    public java.util.List<Map<String, Object>> getActiveStates(@RequestParam String project) {
        return grayscaleService.getActiveStates(project);
    }
}
