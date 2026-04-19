package com.caritasem.ruleuler.server.replay;

import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.model.library.variable.Variable;
import com.bstek.urule.model.library.variable.VariableCategory;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import com.bstek.urule.runtime.KnowledgeSessionFactory;
import com.bstek.urule.runtime.service.KnowledgePackageService;
import com.caritasem.ruleuler.server.replay.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

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
    private final ReplayExportService exportService;
    private final ReplayConfig replayConfig;
    private final KnowledgePackageService knowledgePackageService;

    public ReplayService(ReplayDao replayDao,
                         ReplayClickHouseDao clickHouseDao,
                         RequestReconstructor reconstructor,
                         DiffComparator diffComparator,
                         ReplayExportService exportService,
                         ReplayConfig replayConfig,
                         @Qualifier("urule.knowledgePackageService") KnowledgePackageService knowledgePackageService) {
        this.replayDao = replayDao;
        this.clickHouseDao = clickHouseDao;
        this.reconstructor = reconstructor;
        this.diffComparator = diffComparator;
        this.exportService = exportService;
        this.replayConfig = replayConfig;
        this.knowledgePackageService = knowledgePackageService;
    }

    public long createAndExecute(TrafficQuery query, ToleranceConfig tolerance) {
        if (query.getProject() == null || query.getPackageId() == null) {
            throw new IllegalArgumentException("project 和 packageId 不能为空");
        }
        if (query.getSampleSize() > replayConfig.getMaxSampleSize()) {
            throw new IllegalArgumentException("采样数量超过上限: " + replayConfig.getMaxSampleSize());
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

        // 异步执行（带异常兜底，防止任务卡在 running）
        new Thread(() -> {
            try {
                executeTask(taskId, executionIds, tolerance);
            } catch (Exception e) {
                log.error("回放任务异常终止: taskId={}", taskId, e);
                try { replayDao.updateTaskFinished(taskId, "failed"); } catch (Exception ignored) {}
            }
        }, "replay-" + taskId).start();

        return taskId;
    }

    void executeTask(long taskId, List<String> executionIds, ToleranceConfig tolerance) {
        try {
            replayDao.updateTaskRunning(taskId);
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}", taskId, e);
            return;
        }

        ReplayTask task = replayDao.findTaskById(taskId);
        String missingVarStrategy = task != null ? task.getMissingVarStrategy() : "null";

        // 加载变量定义：category → {varName → Variable}（用于 segment 填充）
        Map<String, Map<String, Variable>> variableDefs = Collections.emptyMap();
        if ("segment".equals(missingVarStrategy) && task != null) {
            variableDefs = loadVariableDefinitions(task.getProject(), task.getPackageId());
        }

        // 预加载知识包（避免每次 session 重复构建）
        String project = task != null ? task.getProject() : null;
        String packageId = task != null ? task.getPackageId() : null;
        String flowId = task != null ? task.getFlowId() : null;
        KnowledgePackage knowledgePackage = null;
        Map<String, String> varCategoryMap = null;
        if (project != null && packageId != null) {
            try {
                String fullPackageId = project + "/" + packageId;
                knowledgePackage = knowledgePackageService.buildKnowledgePackage(fullPackageId);
                varCategoryMap = knowledgePackage != null ? knowledgePackage.getVariableCateogoryMap() : null;
            } catch (Exception e) {
                log.error("加载知识包失败: {}/{}: {}", project, packageId, e.getMessage());
            }
        }

        int executed = 0, match = 0, mismatch = 0, errors = 0, incomplete = 0;
        int batchSize = Math.max(1, executionIds.size() / 20); // 每 5% 批量刷入一次
        List<ReplaySession> sessionBuffer = new ArrayList<>();

        for (String executionId : executionIds) {
            long sessionStart = System.currentTimeMillis();
            try {
                List<Map<String, Object>> varRows = clickHouseDao.fetchVarRows(executionId);
                if (varRows.isEmpty()) {
                    executed++;
                    errors++;
                    sessionBuffer.add(buildErrorSession(taskId, executionId, "无变量数据"));
                    flushIfNeeded(sessionBuffer, batchSize);
                    continue;
                }

                ReconstructResult reconstruct = reconstructor.reconstruct(
                        varRows, null, null, missingVarStrategy, variableDefs);

                if ("skip".equals(missingVarStrategy) && "INCOMPLETE".equals(reconstruct.completenessStatus())) {
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

                // 本地执行回放（不走 HTTP client，直接 KnowledgeSession）
                if (knowledgePackage == null) {
                    errors++;
                    sessionBuffer.add(buildErrorSession(taskId, executionId, "知识包加载失败"));
                    flushIfNeeded(sessionBuffer, batchSize);
                    continue;
                }

                Map<String, Map<String, Object>> replayOutput;
                int execMs;
                try {
                    replayOutput = executeReplay(knowledgePackage, varCategoryMap, flowId, reconstruct.input(), reconstruct.output().keySet());
                    execMs = (int) (System.currentTimeMillis() - sessionStart);
                } catch (Exception e) {
                    errors++;
                    sessionBuffer.add(buildErrorSession(taskId, executionId, "回放执行失败: " + e.getMessage()));
                    flushIfNeeded(sessionBuffer, batchSize);
                    continue;
                }

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

                sessionBuffer.add(ReplaySession.builder()
                        .taskId(taskId)
                        .originalExecutionId(executionId)
                        .replayInput(inputJson)
                        .originalOutput(origOutputJson)
                        .replayOutput(replayOutputJson)
                        .diffResult(diffJson)
                        .completenessStatus(reconstruct.completenessStatus())
                        .execMs(execMs)
                        .originalExecMs(originalExecMs)
                        .status("success")
                        .createdAt(System.currentTimeMillis())
                        .build());
            } catch (Exception e) {
                log.warn("回放执行异常: taskId={}, executionId={}: {}", taskId, executionId, e.getMessage());
                errors++;
                sessionBuffer.add(buildErrorSession(taskId, executionId, e.getMessage()));
            } finally {
                executed++;
                flushIfNeeded(sessionBuffer, batchSize);
            }
        }

        // 刷入剩余 session + 最终进度
        flushSessions(sessionBuffer);
        replayDao.updateTaskProgress(taskId, executed, match, mismatch, errors, incomplete);
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

    /**
     * 本地执行回放：构建 KnowledgeSession，插入 input 事实，startProcess，取 output
     * @param outputCategories output 类别名集合，只有这些类别的 entity 会作为结果返回
     * @return category → {varName → value}，和 reconstruct.output() 格式一致
     */
    private Map<String, Map<String, Object>> executeReplay(KnowledgePackage kp,
                                                            Map<String, String> varCategoryMap,
                                                            String flowId,
                                                            Map<String, Map<String, Object>> input,
                                                            Set<String> outputCategories) {
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(kp);
        Map<String, GeneralEntity> entities = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : input.entrySet()) {
            String category = entry.getKey();
            String clazz = varCategoryMap != null ? varCategoryMap.get(category) : null;
            if (clazz == null) {
                log.warn("回放跳过未知变量类别: {}", category);
                continue;
            }
            GeneralEntity entity = new GeneralEntity(clazz);
            entity.putAll(entry.getValue());
            entities.put(category, entity);
            session.insert(entity);
        }

        // output 类别的 entity 也需要 insert（规则可能读它们的初始值）
        if (outputCategories != null) {
            for (String cat : outputCategories) {
                if (entities.containsKey(cat)) continue; // 已在 input 中
                String clazz = varCategoryMap != null ? varCategoryMap.get(cat) : null;
                if (clazz == null) continue;
                GeneralEntity entity = new GeneralEntity(clazz);
                entities.put(cat, entity);
                session.insert(entity);
            }
        }

        if (flowId != null && !flowId.isEmpty()) {
            session.startProcess(flowId);
        }

        // 只返回 output 类别中相对于 input 有变更的字段
        // 与 ClickHouse 日志记录逻辑对齐：VarEventProducer 只记录 !Objects.equals(oldVal, newVal) 的字段
        Map<String, Map<String, Object>> output = new LinkedHashMap<>();
        Set<String> cats = outputCategories != null ? outputCategories : entities.keySet();
        for (String cat : cats) {
            GeneralEntity entity = entities.get(cat);
            if (entity == null) continue;
            Map<String, Object> inputVars = input.get(cat);
            Map<String, Object> changed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : entity.entrySet()) {
                Object newVal = e.getValue();
                Object oldVal = (inputVars != null) ? inputVars.get(e.getKey()) : null;
                if (Objects.equals(oldVal, newVal)) continue;
                if (newVal == null) continue; // null 值不记录
                changed.put(e.getKey(), newVal);
            }
            if (!changed.isEmpty()) {
                output.put(cat, changed);
            }
        }
        return output;
    }

    private Map<String, Object> filterNullValues(Map<String, Object> map) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v != null && !"".equals(v)) {
                filtered.put(e.getKey(), v);
            }
        }
        return filtered;
    }

    private ReplaySession buildErrorSession(long taskId, String executionId, String errorMessage) {
        return ReplaySession.builder()
                .taskId(taskId)
                .originalExecutionId(executionId)
                .status("error")
                .errorMessage(errorMessage != null && errorMessage.length() > 2000
                        ? errorMessage.substring(0, 2000) : errorMessage)
                .createdAt(System.currentTimeMillis())
                .build();
    }

    private void flushIfNeeded(List<ReplaySession> buffer, int batchSize) {
        if (buffer.size() >= batchSize) {
            flushSessions(buffer);
        }
    }

    private void flushSessions(List<ReplaySession> buffer) {
        if (buffer.isEmpty()) return;
        try {
            replayDao.batchInsertSessions(new ArrayList<>(buffer));
        } catch (Exception e) {
            log.warn("批量写入 session 失败，降级逐条写入: {}", e.getMessage());
            for (ReplaySession s : buffer) {
                try { replayDao.insertSession(s); } catch (Exception ignored) {}
            }
        }
        buffer.clear();
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

    private Map<String, Map<String, Variable>> loadVariableDefinitions(String project, String packageId) {
        try {
            String fullPackageId = project + "/" + packageId;
            KnowledgePackage kp = knowledgePackageService.buildKnowledgePackage(fullPackageId);
            if (kp == null || kp.getRete() == null || kp.getRete().getResourceLibrary() == null) {
                return Collections.emptyMap();
            }
            List<VariableCategory> categories = kp.getRete().getResourceLibrary().getVariableCategories();
            if (categories == null) return Collections.emptyMap();

            Map<String, Map<String, Variable>> result = new LinkedHashMap<>();
            for (VariableCategory vc : categories) {
                List<Variable> vars = vc.getVariables();
                if (vars == null) continue;
                Map<String, Variable> varMap = new LinkedHashMap<>();
                for (Variable v : vars) {
                    varMap.put(v.getName(), v);
                }
                result.put(vc.getName(), varMap);
            }
            return result;
        } catch (Exception e) {
            log.warn("加载变量定义失败: project={}, packageId={}: {}", project, packageId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
