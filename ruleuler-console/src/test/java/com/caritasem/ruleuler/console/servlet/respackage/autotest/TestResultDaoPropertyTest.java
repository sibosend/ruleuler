package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: auto-test-generator, Property 9: 测试用例持久化 round-trip
// Validates: Requirements 4.1, 4.4

import net.jqwik.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestResultDaoPropertyTest {

    private static final JdbcTemplate jdbc;
    private static final TestResultDao dao;

    static {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_dao_" + System.nanoTime())
                .build();
        jdbc = new JdbcTemplate(db);

        jdbc.execute("CREATE TABLE ruleuler_test_run (id BIGINT AUTO_INCREMENT PRIMARY KEY, project VARCHAR(100) NOT NULL, package_id VARCHAR(100) NOT NULL, pack_id BIGINT NOT NULL DEFAULT 0, run_type VARCHAR(20) DEFAULT 'smoke', baseline_run_id BIGINT DEFAULT NULL, total_cases INT NOT NULL DEFAULT 0, executed_cases INT NOT NULL DEFAULT 0, passed_cases INT NOT NULL DEFAULT 0, failed_cases INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'running', started_at BIGINT NOT NULL, finished_at BIGINT)");
        jdbc.execute("CREATE TABLE ruleuler_test_case (id BIGINT AUTO_INCREMENT PRIMARY KEY, pack_id BIGINT NOT NULL, project VARCHAR(100) NOT NULL, package_id VARCHAR(100) NOT NULL, flow_file VARCHAR(500) NOT NULL, case_name VARCHAR(200) NOT NULL, path_description VARCHAR(2000), input_data CLOB NOT NULL, expected_type VARCHAR(20) NOT NULL, flipped_condition VARCHAR(500), test_purpose VARCHAR(500), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");

        dao = new TestResultDao();
        dao.setJdbcTemplate(jdbc);
    }

    @Property(tries = 100)
    void testCaseRoundTrip(@ForAll("randomRunId") int runIdSeed) {
        TestRun run = new TestRun();
        run.setProject("test_project_fixture_001");
        run.setPackageId("pkg_" + runIdSeed);
        run.setStatus("running");
        run.setStartedAt(System.currentTimeMillis());
        long runId = dao.createRun(run);

        TestCase original = new TestCase();
        original.setPackId(runId);
        original.setProject("test_project_fixture_001");
        original.setPackageId("pkg_" + runIdSeed);
        original.setFlowFile("/test/flow.rl.xml");
        original.setCaseName("case_" + runIdSeed);
        original.setPathDescription("Start → End");
        original.setInputData("{\"age\":" + runIdSeed + "}");
        original.setExpectedType(runIdSeed % 2 == 0 ? "HIT" : "MISS");
        original.setFlippedCondition(runIdSeed % 2 == 0 ? null : "age > 10");
        original.setCreatedAt(System.currentTimeMillis());
        original.setUpdatedAt(System.currentTimeMillis());

        dao.batchInsertCases(List.of(original));

        List<TestCase> loaded = dao.findCasesByPackId(runId);
        assertEquals(1, loaded.size());
        TestCase read = loaded.get(0);

        assertEquals(original.getProject(), read.getProject());
        assertEquals(original.getPackageId(), read.getPackageId());
        assertEquals(original.getCaseName(), read.getCaseName());
        assertEquals(original.getInputData(), read.getInputData());
        assertEquals(original.getExpectedType(), read.getExpectedType());
        assertEquals(runId, read.getPackId());
    }

    @Provide
    Arbitrary<Integer> randomRunId() {
        return Arbitraries.integers().between(1, 10000);
    }
}
