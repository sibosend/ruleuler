package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: auto-test-generator, Property 11: 测试执行统计不变量
// Validates: Requirements 5.4

import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.*;
import com.bstek.urule.console.User;
import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestExecutorPropertyTest {

    private static final JdbcTemplate jdbc;
    private static final TestResultDao dao;
    private static final TestExecutor executor;
    private static final TestCaseGenerator gen;

    private static final String FLOW_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<flow-definition>\n" +
            "  <start name=\"S\"><connection to=\"E\"/></start>\n" +
            "  <end name=\"E\"/>\n" +
            "</flow-definition>";

    static {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("testdb_exec_" + System.nanoTime()).build();
        jdbc = new JdbcTemplate(db);

        jdbc.execute("CREATE TABLE ruleuler_test_run (id BIGINT AUTO_INCREMENT PRIMARY KEY, project VARCHAR(100) NOT NULL, package_id VARCHAR(100) NOT NULL, pack_id BIGINT NOT NULL DEFAULT 0, run_type VARCHAR(20) DEFAULT 'smoke', baseline_run_id BIGINT DEFAULT NULL, total_cases INT NOT NULL DEFAULT 0, executed_cases INT NOT NULL DEFAULT 0, passed_cases INT NOT NULL DEFAULT 0, failed_cases INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'running', started_at BIGINT NOT NULL, finished_at BIGINT)");
        jdbc.execute("CREATE TABLE ruleuler_test_case (id BIGINT AUTO_INCREMENT PRIMARY KEY, pack_id BIGINT NOT NULL, project VARCHAR(100) NOT NULL, package_id VARCHAR(100) NOT NULL, flow_file VARCHAR(500) NOT NULL, case_name VARCHAR(200) NOT NULL, path_description VARCHAR(2000), input_data CLOB NOT NULL, expected_type VARCHAR(20) NOT NULL, flipped_condition VARCHAR(500), test_purpose VARCHAR(500), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE ruleuler_test_result (id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id BIGINT NOT NULL, case_id BIGINT NOT NULL, passed TINYINT NOT NULL DEFAULT 0, actual_output CLOB, execution_time_ms BIGINT, error_message CLOB, baseline_output CLOB, diff_status VARCHAR(20), created_at BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE ruleuler_test_case_pack (id BIGINT AUTO_INCREMENT PRIMARY KEY, project VARCHAR(100) NOT NULL, package_id VARCHAR(100) NOT NULL, pack_name VARCHAR(200) NOT NULL, source_type VARCHAR(20) NOT NULL, total_cases INT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE ruleuler_test_segment (id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id BIGINT NOT NULL, variable_name VARCHAR(100) NOT NULL, variable_type VARCHAR(20) NOT NULL, segment_label VARCHAR(200) NOT NULL, case_count INT NOT NULL DEFAULT 0, percentage DECIMAL(5,2) NOT NULL DEFAULT 0, baseline_count INT DEFAULT NULL, baseline_percentage DECIMAL(5,2) DEFAULT NULL, change_pct DECIMAL(5,2) DEFAULT NULL)");
        jdbc.execute("CREATE TABLE ruleuler_test_conflict (id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id BIGINT NOT NULL, conflict_type VARCHAR(20) NOT NULL, severity VARCHAR(10) NOT NULL, rule_file VARCHAR(500) NOT NULL, location VARCHAR(500), description CLOB, created_at BIGINT NOT NULL)");

        dao = new TestResultDao();
        dao.setJdbcTemplate(jdbc);

        RepositoryService mockRepo = new RepositoryService() {
            @Override public InputStream readFile(String path) {
                return new ByteArrayInputStream(FLOW_XML.getBytes(StandardCharsets.UTF_8));
            }
            @Override public InputStream readFile(String p, String v) { throw new UnsupportedOperationException(); }
            @Override public boolean fileExistCheck(String f) { throw new UnsupportedOperationException(); }
            @Override public RepositoryFile createProject(String p, User u, boolean c) { throw new UnsupportedOperationException(); }
            @Override public void createDir(String p, User u) { throw new UnsupportedOperationException(); }
            @Override public void createFile(String p, String c, User u) { throw new UnsupportedOperationException(); }
            @Override public void saveFile(String p, String c, boolean n, String v, User u) { throw new UnsupportedOperationException(); }
            @Override public void deleteFile(String p, User u) { throw new UnsupportedOperationException(); }
            @Override public void lockPath(String p, User u) { throw new UnsupportedOperationException(); }
            @Override public void unlockPath(String p, User u) { throw new UnsupportedOperationException(); }
            @Override public Repository loadRepository(String p, User u, boolean c, FileType[] t, String s) { throw new UnsupportedOperationException(); }
            @Override public void fileRename(String p, String n) { throw new UnsupportedOperationException(); }
            @Override public List<String> getReferenceFiles(String p, String s) { throw new UnsupportedOperationException(); }
            @Override public List<VersionFile> getVersionFiles(String p) { throw new UnsupportedOperationException(); }
            @Override public void exportXml(String p, OutputStream o) { throw new UnsupportedOperationException(); }
            @Override public void importXml(InputStream i, boolean o) { throw new UnsupportedOperationException(); }
            @Override public List<RepositoryFile> getDirectories(String p) { throw new UnsupportedOperationException(); }
            @Override public List<ClientConfig> loadClientConfigs(String p) { throw new UnsupportedOperationException(); }
            @Override public List<com.bstek.urule.console.servlet.permission.UserPermission> loadResourceSecurityConfigs(String c) { throw new UnsupportedOperationException(); }
            @Override public List<RepositoryFile> loadProjects(String c) { throw new UnsupportedOperationException(); }
            @Override public List<ResourcePackage> loadProjectResourcePackages(String p) { throw new UnsupportedOperationException(); }
        };

        gen = new TestCaseGenerator();
        gen.setConditionParser(new ConditionParser(mockRepo));
        gen.setDagPathWalker(new DagPathWalker());
        gen.setValueGenerator(new ValueGenerator());
        gen.setConstraintSolver(new ConstraintSolver());
        gen.setRepositoryService(mockRepo);

        executor = new TestExecutor();
        executor.setKnowledgePackageService(packageInfo -> null);
        executor.setTestCaseGenerator(gen);
        executor.setTestResultDao(dao);
    }

    @Property(tries = 10)
    void passedPlusFailedEqualsTotal(@ForAll("trigger") int ignored) {
        String project = "prop11_" + UUID.randomUUID().toString().substring(0, 8);
        String packageId = "pkg";
        String flowFile = "/test/flow.rl.xml";

        TestCasePack pack = new TestCasePack();
        pack.setProject(project);
        pack.setPackageId(packageId);
        pack.setPackName("test_pack");
        pack.setSourceType("auto");
        pack.setTotalCases(0);
        pack.setCreatedAt(System.currentTimeMillis());
        long packId = dao.createPack(pack);

        List<TestCase> cases = gen.generate(project, packageId, flowFile);
        for (TestCase tc : cases) { tc.setPackId(packId); }
        if (!cases.isEmpty()) { dao.batchInsertCases(cases); }
        jdbc.update("UPDATE ruleuler_test_case_pack SET total_cases=? WHERE id=?", cases.size(), packId);

        TestRun run = executor.execute(packId, null);

        assertEquals(run.getTotalCases(), run.getPassedCases() + run.getFailedCases(),
                "passed + failed must equal total");
        assertEquals("completed", run.getStatus());
    }

    @Provide
    Arbitrary<Integer> trigger() {
        return Arbitraries.integers().between(0, 9);
    }
}
