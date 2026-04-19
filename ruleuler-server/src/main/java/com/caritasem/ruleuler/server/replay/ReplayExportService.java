package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestCase;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestCasePack;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestResultDao;
import com.caritasem.ruleuler.server.replay.model.ReplaySession;
import com.caritasem.ruleuler.server.replay.model.ReplayTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ReplayExportService {

    @Autowired
    private ReplayDao replayDao;

    @Autowired
    @Qualifier("urule.testResultDao")
    private TestResultDao testResultDao;

    public long exportToTestCasePack(long taskId, String scope) {
        ReplayTask task = replayDao.findTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在");
        if (!"completed".equals(task.getStatus())) throw new IllegalArgumentException("任务未完成，无法导出");

        List<ReplaySession> allSessions = replayDao.findSessionsByTaskIdAll(taskId);

        Predicate<ReplaySession> filter = switch (scope) {
            case "mismatch" -> s -> "success".equals(s.getStatus()) && s.getDiffResult() != null
                    && s.getDiffResult().contains("\"match\":false");
            case "match" -> s -> "success".equals(s.getStatus()) && s.getDiffResult() != null
                    && s.getDiffResult().contains("\"match\":true");
            default -> s -> "success".equals(s.getStatus());
        };

        List<ReplaySession> filtered = allSessions.stream().filter(filter).toList();
        if (filtered.isEmpty()) throw new IllegalArgumentException("没有可导出的会话");

        // 创建 TestCasePack
        long now = System.currentTimeMillis();
        TestCasePack pack = new TestCasePack();
        pack.setProject(task.getProject());
        pack.setPackageId(task.getPackageId());
        pack.setPackName("回放导出_" + task.getId() + "_" + scope);
        pack.setSourceType("replay");
        pack.setTotalCases(filtered.size());
        pack.setCreatedAt(now);
        long packId = testResultDao.createPack(pack);

        // 创建 TestCase 列表
        List<TestCase> cases = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            ReplaySession session = filtered.get(i);
            TestCase tc = new TestCase();
            tc.setPackId(packId);
            tc.setProject(task.getProject());
            tc.setPackageId(task.getPackageId());
            tc.setCaseName("REPLAY_" + (i + 1));
            tc.setInputData(session.getReplayInput());
            tc.setExpectedType("HIT");
            tc.setTestPurpose("流量回放导出: " + session.getOriginalExecutionId());
            tc.setCreatedAt(now);
            tc.setUpdatedAt(now);
            cases.add(tc);
        }
        testResultDao.batchInsertCases(cases);

        return packId;
    }
}
