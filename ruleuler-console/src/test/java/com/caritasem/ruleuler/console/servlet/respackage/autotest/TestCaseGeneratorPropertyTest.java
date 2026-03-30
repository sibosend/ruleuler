package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: auto-test-generator, Property 10: 重新生成幂等性
// Validates: Requirements 4.3

import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.*;
import com.bstek.urule.console.User;

import net.jqwik.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseGeneratorPropertyTest {

    private static final TestCaseGenerator generator;

    private static final String FLOW_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<flow-definition>\n" +
            "  <start name=\"开始\"><connection to=\"决策\"/></start>\n" +
            "  <decision name=\"决策\">\n" +
            "    <item connection=\"rule0\">参数.age 大于 10</item>\n" +
            "    <item connection=\"rule1\">参数.age 小于等于 10</item>\n" +
            "  </decision>\n" +
            "  <rule name=\"rule0\" file=\"/test/rule0.rs.xml\"><connection to=\"结束\"/></rule>\n" +
            "  <rule name=\"rule1\" file=\"/test/rule1.rs.xml\"><connection to=\"结束\"/></rule>\n" +
            "  <end name=\"结束\"/>\n" +
            "</flow-definition>";

    private static final String RULE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<rule-set><rule><if><and>" +
            "<atom op=\"GreaterThen\"><left type=\"variable\" var=\"score\" datatype=\"Integer\"/>" +
            "<value type=\"Input\" content=\"60\"/></atom>" +
            "</and></if></rule></rule-set>";

    static {
        RepositoryService mockRepo = createStubRepo();
        generator = new TestCaseGenerator();
        generator.setConditionParser(new ConditionParser(mockRepo));
        generator.setDagPathWalker(new DagPathWalker());
        generator.setValueGenerator(new ValueGenerator());
        generator.setConstraintSolver(new ConstraintSolver());
        generator.setRepositoryService(mockRepo);
    }

    @Property(tries = 100)
    void regenerateProducesSameCount(@ForAll("packageIds") String packageId) {
        String project = "test_project_fixture_001";
        String flowFile = "/test/flow.rl.xml";
        List<TestCase> first = generator.generate(project, packageId, flowFile);
        List<TestCase> second = generator.generate(project, packageId, flowFile);
        assertEquals(first.size(), second.size(), "同样输入两次生成应产生相同数量的用例");
    }

    @Provide
    Arbitrary<String> packageIds() {
        return Arbitraries.integers().between(1, 10000).map(i -> "pkg_" + i);
    }

    private static RepositoryService createStubRepo() {
        return new RepositoryService() {
            @Override public InputStream readFile(String path) {
                String xml = path.contains("flow") ? FLOW_XML : RULE_XML;
                return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
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
    }
}
