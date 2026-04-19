package com.caritasem.ruleuler.server.replay;

import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.RepositoryService;
import com.caritasem.ruleuler.server.replay.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReplayDao replayDao;
    private final ReplayClickHouseDao clickHouseDao;
    private final RequestReconstructor reconstructor;
    private final DiffComparator diffComparator;
    private final RepositoryService repositoryService;
    private final ReplayExportService exportService;

    public ReplayService(ReplayDao replayDao,
                         ReplayClickHouseDao clickHouseDao,
                         RequestReconstructor reconstructor,
                         DiffComparator diffComparator,
                         @Qualifier("urule.repositoryService") RepositoryService repositoryService,
                         ReplayExportService exportService) {
        this.replayDao = replayDao;
        this.clickHouseDao = clickHouseDao;
        this.reconstructor = reconstructor;
        this.diffComparator = diffComparator;
        this.repositoryService = repositoryService;
        this.exportService = exportService;
    }

    public long createAndExecute(TrafficQuery query, ToleranceConfig tolerance) {
        if (query.getProject() == null || query.getPackageId() == null) {
            throw new IllegalArgumentException("project 和 packageId 不能为空");
        }
        if (query.getStartTime() == null) {
            query.setStartTime(System.currentTimeMillis() - 86400000L);
        }
        if (query.getEndTime() == null) {
            query.setEndTime(System.currentTimeMillis());
        }

        // 采样 execution_id
        List<String> executionIds;
        long totalCount = clickHouseDao.countDistinctExecutions(query);

        if ("all".equals(query.getSampleStrategy())) {
            executionIds = clickHouseDao.fetchAllExecutionIds(query);
        } else if ("uniform".equals(query.getSampleStrategy())) {
            executionIds = clickHouseDao.fetchUniformSample(query);
        } else {
            executionIds = clickHouseDao.fetchRandomSample(query, totalCount);
        }

        // 推断 flowId
        String flowId = query.getFlowId();
        if (flowId == null && !executionIds.isEmpty()) {
            flowId = inferFlowId(executionIds.get(0));
        }

        long now = System.currentTimeMillis();
        String queryJson;
        try {
            queryJson = objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            queryJson = "{}";
        }
        String toleranceJson;
        try {
            toleranceJson = tolerance != null ? objectMapper.writeValueAsString(tolerance) : null;
        } catch (Exception e) {
            toleranceJson = null;
        }

        ReplayTask task = ReplayTask.builder()
                .project(query.getProject())
                .packageId(query.getPackageId())
                .flowId(flowId)
                .trafficQuery(queryJson)
                .sampleStrategy(query.getSampleStrategy())
                .sampleSize(query.getSampleSize())
                .missingVarStrategy(query.getMissingVarStrategy())
                .totalCount(executionIds.size())
                .status("pending")
                .toleranceConfig(toleranceJson)
                .createdAt(now)
                .build();

        long taskId = replayDao.insertTask(task);

        // 异步执行
        new Thread(() -> executeTask(taskId, executionIds, tolerance), "replay-" + taskId).start();

        return taskId;
    }

    void executeTask(long taskId, List<String> executionIds, ToleranceConfig tolerance) {
        try {
            replayDao.updateTaskRunning(taskId);
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}", taskId, e);
            return;
        }

        int executed = 0, match = 0, mismatch = 0, errors = 0, incomplete = 0;

        for (String executionId : executionIds) {
            long sessionStart = System.currentTimeMillis();
            try {
                List<Map<String, Object>> varRows = clickHouseDao.fetchVarRows(executionId);
                if (varRows.isEmpty()) {
                    executed++;
                    errors++;
                    saveErrorSession(taskId, executionId, "无变量数据");
                    continue;
                }

                ReconstructResult reconstruct = reconstructor.reconstruct(
                        varRows, null, null, "null");

                if ("skip".equals(/* from task's strategy */ "null") && "INCOMPLETE".equals(reconstruct.completenessStatus())) {
                    incomplete++;
                    executed++;
                    continue;
                }

                // 获取原始输出中的 exec_ms
                Integer originalExecMs = null;
                for (Map<String, Object> row : varRows) {
                    Object em = row.get("exec_ms");
                    if (em instanceof Number) {
                        originalExecMs = ((Number) em).intValue();
                        break;
                    }
                }

                // 调用 client 执行回放
                Map<String, Object> replayResult = callClientReplay(
                        reconstruct.input(), taskId);

                int execMs = (int) (System.currentTimeMillis() - sessionStart);

                if (replayResult == null || replayResult.containsKey("error")) {
                    errors++;
                    saveErrorSession(taskId, executionId,
                            replayResult != null ? String.valueOf(replayResult.get("error")) : "调用失败");
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> replayOutput = (Map<String, Object>) replayResult.get("data");

                    // 比对差异
                    ToleranceConfig tol = tolerance != null ? tolerance : ToleranceConfig.exact();
                    DiffResult diff = diffComparator.compare(reconstruct.output(), replayOutput, tol);

                    String diffJson;
                    try {
                        diffJson = objectMapper.writeValueAsString(diff);
                    } catch (Exception e) {
                        diffJson = "{\"match\":" + diff.match() + "}";
                    }

                    String inputJson, origOutputJson, replayOutputJson;
                    try {
                        inputJson = objectMapper.writeValueAsString(reconstruct.input());
                        origOutputJson = objectMapper.writeValueAsString(reconstruct.output());
                        replayOutputJson = objectMapper.writeValueAsString(replayOutput);
                    } catch (Exception e) {
                        inputJson = "{}";
                        origOutputJson = "{}";
                        replayOutputJson = "{}";
                    }

                    boolean isMatch = diff.match();
                    if (isMatch) match++; else mismatch++;
                    if ("INCOMPLETE".equals(reconstruct.completenessStatus())) incomplete++;

                    // 截断过大 JSON（64KB 限制）
                    inputJson = truncateJson(inputJson);
                    origOutputJson = truncateJson(origOutputJson);
                    replayOutputJson = truncateJson(replayOutputJson);

                    Integer clientExecMs = replayResult.get("execMs") != null
                            ? ((Number) replayResult.get("execMs")).intValue() : null;

                    ReplaySession session = ReplaySession.builder()
                            .taskId(taskId)
                            .originalExecutionId(executionId)
                            .replayInput(inputJson)
                            .originalOutput(origOutputJson)
                            .replayOutput(replayOutputJson)
                            .diffResult(diffJson)
                            .completenessStatus(reconstruct.completenessStatus())
                            .execMs(clientExecMs != null ? clientExecMs : execMs)
                            .originalExecMs(originalExecMs)
                            .status("success")
                            .createdAt(System.currentTimeMillis())
                            .build();
                    replayDao.insertSession(session);
                }
            } catch (Exception e) {
                log.warn("回放执行异常: taskId={}, executionId={}: {}", taskId, executionId, e.getMessage());
                errors++;
                saveErrorSession(taskId, executionId, e.getMessage());
            } finally {
                executed++;
                replayDao.updateTaskProgress(taskId, executed, match, mismatch, errors, incomplete);
            }
        }

        String finalStatus = executed > 0 && (match + mismatch) > 0 ? "completed" : "failed";
        replayDao.updateTaskFinished(taskId, finalStatus);
    }

    public ReplayTask getTask(long taskId) {
        return replayDao.findTaskById(taskId);
    }

    public Map<String, Object> listTasks(String project, String packageId, String status,
                                          int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ReplayTask> items = replayDao.listTasks(project, packageId, status, offset, pageSize);
        int total = replayDao.countTasks(project, packageId, status);
        return Map.of("items", items, "total", total);
    }

    public Map<String, Object> listSessions(long taskId, String statusFilter, Boolean matchFilter,
                                             int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ReplaySession> items = replayDao.findSessionsByTaskId(taskId, statusFilter, matchFilter, offset, pageSize);
        int total = replayDao.countSessions(taskId, statusFilter, matchFilter);
        return Map.of("items", items, "total", total);
    }

    public Map<String, Object> getReport(long taskId) {
        ReplayTask task = replayDao.findTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");

        List<ReplaySession> allSessions = replayDao.findSessionsByTaskIdAll(taskId);

        // 变量维度统计
        Map<String, int[]> varStats = new LinkedHashMap<>();
        for (ReplaySession s : allSessions) {
            if (!"success".equals(s.getStatus()) || s.getDiffResult() == null) continue;
            try {
                Map<String, Object> dr = objectMapper.readValue(s.getDiffResult(), java.util.Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) dr.get("fields");
                if (fields == null) continue;
                for (Map<String, Object> f : fields) {
                    String key = f.get("category") + "." + f.get("name");
                    int[] counts = varStats.computeIfAbsent(key, k -> new int[2]);
                    counts[1]++; // total compared
                    if ("CHANGED".equals(f.get("status"))) counts[0]++; // changed
                }
            } catch (Exception ignored) {}
        }
        List<ReplayReport.VariableStat> variableStats = varStats.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\.", 2);
                    int changed = e.getValue()[0];
                    int total = e.getValue()[1];
                    return new ReplayReport.VariableStat(
                            parts[0], parts.length > 1 ? parts[1] : "",
                            changed, total, total > 0 ? (double) changed / total : 0);
                })
                .collect(Collectors.toList());

        // 耗时对比
        List<Integer> origTimes = new ArrayList<>();
        List<Integer> replayTimes = new ArrayList<>();
        for (ReplaySession s : allSessions) {
            if (s.getOriginalExecMs() != null) origTimes.add(s.getOriginalExecMs());
            if (s.getExecMs() != null) replayTimes.add(s.getExecMs());
        }
        Collections.sort(origTimes);
        Collections.sort(replayTimes);

        ReplayReport.TimingComparison timing = new ReplayReport.TimingComparison(
                avg(origTimes), avg(replayTimes),
                percentile(origTimes, 50), percentile(replayTimes, 50),
                percentile(origTimes, 95), percentile(replayTimes, 95)
        );

        ReplayReport.Summary summary = new ReplayReport.Summary(
                task.getTotalCount(), task.getMatchCount(), task.getMismatchCount(),
                task.getErrorCount(), task.getIncompleteCount(),
                task.getExecutedCount() > 0 ? (double) task.getMatchCount() / task.getExecutedCount() : 0
        );

        return Map.of(
                "task", task,
                "summary", summary,
                "variableStats", variableStats,
                "timing", timing
        );
    }

    // ---- helpers ----

    private String inferFlowId(String executionId) {
        try {
            List<Map<String, Object>> rows = clickHouseDao.fetchVarRows(executionId);
            for (Map<String, Object> row : rows) {
                Object flowId = row.get("flow_id");
                if (flowId != null && !flowId.toString().isEmpty()) {
                    return flowId.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> callClientReplay(Map<String, Map<String, Object>> input, long taskId) {
        try {
            List<ClientConfig> clients = repositoryService.loadClientConfigs(null);
            if (clients.isEmpty()) {
                log.warn("无可用的 client 配置");
                return Map.of("error", "无可用 client");
            }
            String clientUrl = clients.get(0).getClient();
            if (clientUrl.endsWith("/")) clientUrl = clientUrl.substring(0, clientUrl.length() - 1);

            // 先获取 task 信息来构造请求
            ReplayTask task = replayDao.findTaskById(taskId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("project", task.getProject());
            payload.put("packageId", task.getPackageId());
            payload.put("flowId", task.getFlowId());
            payload.put("input", input);

            String json = objectMapper.writeValueAsString(payload);
            URL url = new URL(clientUrl + "/api/replay/execute");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() >= 300) {
                String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                return Map.of("error", "HTTP " + conn.getResponseCode() + ": " + err);
            }
            String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();
            return objectMapper.readValue(resp, java.util.Map.class);
        } catch (Exception e) {
            log.warn("调用 client 回放失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private void saveErrorSession(long taskId, String executionId, String errorMessage) {
        try {
            replayDao.insertSession(ReplaySession.builder()
                    .taskId(taskId)
                    .originalExecutionId(executionId)
                    .status("error")
                    .errorMessage(errorMessage != null && errorMessage.length() > 2000
                            ? errorMessage.substring(0, 2000) : errorMessage)
                    .createdAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            log.warn("写入错误 session 失败: {}", e.getMessage());
        }
    }

    private String truncateJson(String json) {
        if (json != null && json.length() > 65536) {
            log.warn("JSON 超过 64KB，截断处理");
            return json.substring(0, 65536);
        }
        return json;
    }

    private double avg(List<Integer> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    public long exportToTestCasePack(long taskId, String scope) {
        return exportService.exportToTestCasePack(taskId, scope);
    }

    private double percentile(List<Integer> sortedValues, int pct) {
        if (sortedValues.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(idx, sortedValues.size() - 1)));
    }
}
