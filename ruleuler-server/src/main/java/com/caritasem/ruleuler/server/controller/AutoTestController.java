package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.ResourceItem;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.caritasem.ruleuler.server.auth.ApiResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/autotest")
public class AutoTestController {

    private final TestExecutor testExecutor;
    private final TestResultDao testResultDao;
    private final RepositoryService repositoryService;
    private final TestCaseGenerator testCaseGenerator;

    public AutoTestController(
            @Qualifier("urule.testExecutor") TestExecutor testExecutor,
            @Qualifier("urule.testResultDao") TestResultDao testResultDao,
            @Qualifier("urule.repositoryService") RepositoryService repositoryService,
            @Qualifier("urule.testCaseGenerator") TestCaseGenerator testCaseGenerator) {
        this.testExecutor = testExecutor;
        this.testResultDao = testResultDao;
        this.repositoryService = repositoryService;
        this.testCaseGenerator = testCaseGenerator;
    }

    // ==================== V2 API ====================

    /** V2: 自动生成用例包 */
    @PostMapping("/packs/generate")
    public ApiResult generatePack(@RequestParam String project, @RequestParam String packageId) {
        project = stripLeadingSlash(project);
        ResourcePackage targetPkg = findResourcePackage(project, packageId);
        if (targetPkg == null) {
            return ApiResult.error(400, "知识包不存在: " + packageId);
        }
        String flowFile = findFlowFile(targetPkg);
        if (flowFile == null) {
            return ApiResult.error(400, "该知识包不包含决策流，无法生成测试用例");
        }
        TestCasePack pack = testCaseGenerator.generatePack(project, packageId, flowFile);
        return ApiResult.ok(pack);
    }

    /** V2: 手动导入用例包（CSV/JSON 文件上传） */
    @PostMapping("/packs/import")
    public ApiResult importPack(@RequestParam String project,
                                @RequestParam String packageId,
                                @RequestParam("file") MultipartFile file) {
        project = stripLeadingSlash(project);
        if (file.isEmpty()) {
            return ApiResult.error(400, "上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ApiResult.error(400, "无法获取文件名");
        }
        String lowerName = filename.toLowerCase();

        List<Map<String, Object>> rows;
        try {
            if (lowerName.endsWith(".csv")) {
                rows = parseCsv(file);
            } else if (lowerName.endsWith(".json")) {
                rows = parseJson(file);
            } else {
                return ApiResult.error(400, "不支持的文件格式，仅支持 .csv 和 .json");
            }
        } catch (ImportException e) {
            return ApiResult.error(400, e.getMessage());
        }

        if (rows.isEmpty()) {
            return ApiResult.error(400, "文件中没有有效数据行");
        }

        // 创建 pack
        long now = System.currentTimeMillis();
        TestCasePack pack = new TestCasePack();
        pack.setProject(project);
        pack.setPackageId(packageId);
        pack.setPackName("手动导入_" + filename);
        pack.setSourceType("manual");
        pack.setTotalCases(rows.size());
        pack.setCreatedAt(now);
        long packId = testResultDao.createPack(pack);
        pack.setId(packId);

        // 构建用例
        ObjectMapper mapper = new ObjectMapper();
        List<TestCase> cases = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            TestCase tc = new TestCase();
            tc.setPackId(packId);
            tc.setProject(project);
            tc.setPackageId(packageId);
            tc.setCaseName("IMPORT_" + (i + 1));
            try {
                tc.setInputData(mapper.writeValueAsString(rows.get(i)));
            } catch (Exception e) {
                throw new RuntimeException("序列化第 " + (i + 1) + " 行数据失败", e);
            }
            tc.setExpectedType("HIT");
            tc.setTestPurpose("手动导入");
            tc.setCreatedAt(now);
            tc.setUpdatedAt(now);
            cases.add(tc);
        }
        testResultDao.batchInsertCases(cases);

        return ApiResult.ok(pack);
    }

    /** V2: 查询用例包列表 */
    @GetMapping("/packs")
    public ApiResult listPacks(@RequestParam String project, @RequestParam(required = false) String packageId) {
        project = stripLeadingSlash(project);
        return ApiResult.ok(testResultDao.findPacksByPackage(project, packageId));
    }

    /** V2: 查询用例包详情（含用例列表） */
    @GetMapping("/packs/{packId}")
    public ApiResult getPackDetail(@PathVariable Long packId) {
        TestCasePack pack = testResultDao.findPackById(packId);
        if (pack == null) {
            return ApiResult.error(404, "用例包不存在: " + packId);
        }
        List<TestCase> cases = testResultDao.findCasesByPackId(packId);
        Map<String, Object> result = new HashMap<>();
        result.put("pack", pack);
        result.put("cases", cases);
        return ApiResult.ok(result);
    }

    /** V2: 查询运行列表 */
    @GetMapping("/runs")
    public ApiResult listRuns(@RequestParam String project,
                              @RequestParam(required = false) String packageId) {
        project = stripLeadingSlash(project);
        return ApiResult.ok(testResultDao.findRunsByPackage(project, packageId));
    }

    /** V2: 执行测试运行 */
    @PostMapping("/runs")
    public ApiResult executeRun(@RequestParam Long packId,
                                @RequestParam(required = false) Long baselineRunId) {
        TestCasePack pack = testResultDao.findPackById(packId);
        if (pack == null) {
            return ApiResult.error(400, "用例包不存在: " + packId);
        }
        if (baselineRunId != null) {
            TestRun baselineRun = testResultDao.findRunById(baselineRunId);
            if (baselineRun == null) {
                return ApiResult.error(400, "baseline_run_id 指向的 run 不存在: " + baselineRunId);
            }
        }
        TestRun run = testExecutor.execute(packId, baselineRunId);
        return ApiResult.ok(run);
    }

    /** V2: 查询运行进度 */
    @GetMapping("/run-status")
    public ApiResult getRunStatus(@RequestParam Long runId) {
        TestRun run = testResultDao.findRunById(runId);
        if (run == null) {
            return ApiResult.error(404, "运行记录不存在: " + runId);
        }
        Map<String, Object> status = new HashMap<>();
        status.put("runId", run.getId());
        status.put("status", run.getStatus());
        status.put("executedCases", run.getExecutedCases());
        status.put("totalCases", run.getTotalCases());
        return ApiResult.ok(status);
    }

    /** V2: 查询冲突检测结果 */
    @GetMapping("/conflicts/{runId}")
    public ApiResult getConflicts(@PathVariable Long runId) {
        return ApiResult.ok(testResultDao.findConflictsByRunId(runId));
    }

    /** V2: 测试报告 — 汇总指标 + 分布统计 + 变化明细 */
    @GetMapping("/report/{runId}")
    public ApiResult getReport(@PathVariable Long runId) {
        TestRun run = testResultDao.findRunById(runId);
        if (run == null) {
            return ApiResult.error(404, "运行记录不存在: " + runId);
        }

        List<TestResult> results = testResultDao.findResultsByRunId(runId);

        // 汇总指标
        int totalCases = results.size();
        int sameCount = 0;
        int changedCount = 0;
        List<TestResult> changeDetails = new ArrayList<>();
        for (TestResult r : results) {
            String ds = r.getDiffStatus();
            if ("SAME".equals(ds) || "BASELINE".equals(ds)) {
                sameCount++;
            } else if ("CHANGED".equals(ds)) {
                changedCount++;
                changeDetails.add(r);
            }
        }
        double consistencyRate = totalCases > 0 ? (double) sameCount / totalCases : 1.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCases", totalCases);
        summary.put("sameCount", sameCount);
        summary.put("changedCount", changedCount);
        summary.put("consistencyRate", consistencyRate);

        // 分布统计
        List<TestSegment> segments = testResultDao.findSegmentsByRunId(runId);

        // 组装报告
        Map<String, Object> report = new HashMap<>();
        report.put("run", run);
        report.put("summary", summary);
        report.put("segments", segments);
        report.put("changeDetails", changeDetails);
        return ApiResult.ok(report);
    }

    /** V2: 查询分段统计 */
    @GetMapping("/segments/{runId}")
    public ApiResult getSegments(@PathVariable Long runId) {
        return ApiResult.ok(testResultDao.findSegmentsByRunId(runId));
    }

    /** V2: 更新 baseline — 将指定 run 设为新 baseline */
    @PutMapping("/baseline/{runId}")
    public ApiResult updateBaseline(@PathVariable Long runId) {
        TestRun run = testResultDao.findRunById(runId);
        if (run == null) {
            return ApiResult.error(404, "运行记录不存在: " + runId);
        }
        // 将该 run 的所有结果标记为 BASELINE
        testResultDao.setAllDiffStatus(runId, "BASELINE");
        return ApiResult.ok("baseline 已更新为 runId=" + runId);
    }

    // ==================== 内部方法 ====================

    private String stripLeadingSlash(String project) {
        return (project != null && project.startsWith("/")) ? project.substring(1) : project;
    }

    private ResourcePackage findResourcePackage(String project, String packageId) {
        try {
            List<ResourcePackage> packages = repositoryService.loadProjectResourcePackages(project);
            for (ResourcePackage pkg : packages) {
                if (pkg.getId().equals(packageId)) {
                    return pkg;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("加载知识包列表失败: " + project, e);
        }
        return null;
    }

    private String findFlowFile(ResourcePackage pkg) {
        if (pkg.getResourceItems() == null) return null;
        for (ResourceItem item : pkg.getResourceItems()) {
            String path = item.getPath();
            if (path != null && path.endsWith(".rl.xml")) {
                int colonIdx = path.indexOf(":");
                if (colonIdx > 0 && colonIdx < 5) {
                    path = path.substring(colonIdx + 1);
                }
                return path;
            }
        }
        return null;
    }

    // ==================== 文件解析 ====================

    private List<Map<String, Object>> parseCsv(MultipartFile file) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new ImportException("CSV 文件为空或缺少表头");
            }
            String[] headers = headerLine.split(",", -1);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
                if (headers[i].isEmpty()) {
                    throw new ImportException("CSV 表头第 " + (i + 1) + " 列为空");
                }
            }

            String line;
            int lineNum = 1; // 数据行从 1 开始（表头之后）
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;
                String[] values = line.split(",", -1);
                if (values.length != headers.length) {
                    throw new ImportException("CSV 第 " + lineNum + " 行列数(" + values.length
                            + ")与表头列数(" + headers.length + ")不匹配");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], values[i].trim());
                }
                rows.add(row);
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("CSV 解析失败: " + e.getMessage());
        }
        return rows;
    }

    private List<Map<String, Object>> parseJson(MultipartFile file) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<Map<String, Object>> rows = mapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {});
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                if (row == null || row.isEmpty()) {
                    throw new ImportException("JSON 第 " + (i + 1) + " 个元素为空对象");
                }
            }
            return rows;
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("JSON 解析失败: " + e.getMessage());
        }
    }

    /** 导入异常，用于统一返回 400 */
    static class ImportException extends RuntimeException {
        ImportException(String message) { super(message); }
    }
}
