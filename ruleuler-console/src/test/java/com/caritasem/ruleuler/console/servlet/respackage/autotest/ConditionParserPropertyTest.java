package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: auto-test-generator, Property 1: 条件解析完整性
// Validates: Requirements 1.4, 1.5, 1.6

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.*;
import com.bstek.urule.console.User;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 属性测试 — 条件解析完整性 (Property 1)
 */
class ConditionParserPropertyTest {

    private static final Datatype[] SUPPORTED_TYPES = {
            Datatype.String, Datatype.Integer, Datatype.Double, Datatype.Long,
            Datatype.Float, Datatype.BigDecimal, Datatype.Boolean, Datatype.Date
    };

    private static final Set<Op> NO_VALUE_OPS = EnumSet.of(Op.Null, Op.NotNull);

    @Property(tries = 100)
    void parsedAtomFieldsAreNeverNull(
            @ForAll("opAndDatatype") Tuple2<Op, Datatype> combo
    ) throws Exception {
        Op op = combo.get1();
        Datatype datatype = combo.get2();

        String varName = "testVar";
        String varLabel = "测试变量";
        String value = sampleValue(datatype);

        String xml = buildRulEulerml(op, datatype, varName, varLabel, value);
        String filePath = "/test_project_fixture_001/rule.rs.xml";

        RepositoryService mockRepo = stubRepositoryService(filePath, xml);
        ConditionParser parser = new ConditionParser(mockRepo);

        List<ConditionConstraint> constraints = parser.parseRuleFile(filePath);

        assertEquals(1, constraints.size(), "应解析出恰好 1 个约束");
        ConditionConstraint cc = constraints.get(0);

        assertNotNull(cc.getVariableName(), "variableName 不应为 null");
        assertNotNull(cc.getOp(), "op 不应为 null");
        assertNotNull(cc.getDatatype(), "datatype 不应为 null");

        if (!NO_VALUE_OPS.contains(op)) {
            assertNotNull(cc.getValue(), "非 Null/NotNull 操作符时 value 不应为 null");
        }
    }

    @Provide
    Arbitrary<Tuple2<Op, Datatype>> opAndDatatype() {
        Arbitrary<Op> ops = Arbitraries.of(Op.values());
        Arbitrary<Datatype> types = Arbitraries.of(SUPPORTED_TYPES);
        return Combinators.combine(ops, types).as(Tuple::of);
    }

    private static String sampleValue(Datatype dt) {
        switch (dt) {
            case Integer: case Long: return "42";
            case Double: case Float: case BigDecimal: return "3.14";
            case Boolean: return "true";
            case Date: return "2024-01-01 00:00:00";
            default: return "hello";
        }
    }

    private static String buildRulEulerml(Op op, Datatype datatype, String varName, String varLabel, String value) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rule-set>\n");
        sb.append("  <rule>\n");
        sb.append("    <if>\n");
        sb.append("      <and>\n");
        sb.append("        <atom op=\"").append(op.name()).append("\">\n");
        sb.append("          <left type=\"variable\" var=\"").append(varName)
          .append("\" var-label=\"").append(varLabel)
          .append("\" datatype=\"").append(datatype.name()).append("\"/>\n");
        if (!NO_VALUE_OPS.contains(op)) {
            sb.append("          <value type=\"Input\" content=\"").append(value).append("\"/>\n");
        }
        sb.append("        </atom>\n");
        sb.append("      </and>\n");
        sb.append("    </if>\n");
        sb.append("  </rule>\n");
        sb.append("</rule-set>\n");
        return sb.toString();
    }

    private static RepositoryService stubRepositoryService(String expectedPath, String xmlContent) {
        return new RepositoryService() {
            @Override
            public InputStream readFile(String path) {
                return new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            }
            @Override public InputStream readFile(String path, String version) { throw new UnsupportedOperationException(); }
            @Override public boolean fileExistCheck(String filePath) { throw new UnsupportedOperationException(); }
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
            @Override public void exportXml(String p, java.io.OutputStream o) { throw new UnsupportedOperationException(); }
            @Override public void importXml(InputStream i, boolean o) { throw new UnsupportedOperationException(); }
            @Override public List<RepositoryFile> getDirectories(String p) { throw new UnsupportedOperationException(); }
            @Override public List<ClientConfig> loadClientConfigs(String p) { throw new UnsupportedOperationException(); }
            @Override public List<com.bstek.urule.console.servlet.permission.UserPermission> loadResourceSecurityConfigs(String c) { throw new UnsupportedOperationException(); }
            @Override public List<RepositoryFile> loadProjects(String c) { throw new UnsupportedOperationException(); }
            @Override public List<ResourcePackage> loadProjectResourcePackages(String p) { throw new UnsupportedOperationException(); }
        };
    }
}
