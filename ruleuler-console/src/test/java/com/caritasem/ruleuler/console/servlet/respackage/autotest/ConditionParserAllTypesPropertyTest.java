package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: autotest-v2, Property 4: 决策表解析——行约束数等于 Criteria 列数
// Feature: autotest-v2, Property 5: 决策树解析——约束组数等于叶节点数
// Feature: autotest-v2, Property 6: 评分卡解析——约束数等于有条件的行数
// Validates: Requirements 2.2, 2.3, 2.4

import com.bstek.urule.model.rule.Op;
import com.bstek.urule.console.repository.*;
import com.bstek.urule.console.repository.model.*;
import com.bstek.urule.console.User;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConditionParser 全类型解析属性测试。
 * 直接调用 parseDecisionTable / parseDecisionTree / parseScorecard 方法，
 * 传入随机生成的 XML Element，验证解析结果的结构性不变量。
 */
class ConditionParserAllTypesPropertyTest {

    private final ConditionParser parser = new ConditionParser(dummyRepo());

    private static RepositoryService dummyRepo() {
        return new RepositoryService() {
            @Override public InputStream readFile(String p) { throw new UnsupportedOperationException(); }
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

    // ========== 可用的 Op 子集（带 value 的比较操作符） ==========
    private static final Op[] COMPARISON_OPS = {
            Op.Equals, Op.NotEquals, Op.GreaterThen, Op.GreaterThenEquals,
            Op.LessThen, Op.LessThenEquals
    };

    // =====================================================================
    // Property 4: 决策表解析——行约束数等于 Criteria 列数（排除值为空的单元格）
    // =====================================================================

    /**
     * Feature: autotest-v2, Property 4: 决策表解析——行约束数等于 Criteria 列数
     *
     * 对于任意决策表 XML，parseDecisionTable() 解析后，
     * 每行产生的约束数应等于该决策表中 type="Criteria" 的列数（排除值为空的单元格）。
     *
     * 生成策略：随机 1~4 列 Criteria + 1~5 行，每个 cell 随机填充或留空。
     * 总约束数 = 所有行中非空 cell 的数量。
     */
    @Property(tries = 100)
    void decisionTableConstraintCountEqualsCriteriaCols(
            @ForAll("decisionTableSpec") Tuple2<String, Integer> spec
    ) throws Exception {
        String xml = spec.get1();
        int expectedTotal = spec.get2();

        Element root = parseXml(xml);
        List<ConditionConstraint> constraints = parser.parseDecisionTable(root, "test.dt.xml");

        assertEquals(expectedTotal, constraints.size(),
                "总约束数应等于所有行中非空 Criteria cell 的数量");
    }

    @Provide
    Arbitrary<Tuple2<String, Integer>> decisionTableSpec() {
        Arbitrary<Integer> colCounts = Arbitraries.integers().between(1, 4);
        Arbitrary<Integer> rowCounts = Arbitraries.integers().between(1, 5);

        return Combinators.combine(colCounts, rowCounts).flatAs((numCols, numRows) -> {
            // 为每个 (row, col) 随机决定是否填充
            int totalCells = numRows * numCols;
            return Arbitraries.of(true, false).list().ofSize(totalCells).map(fills -> {
                StringBuilder sb = new StringBuilder();
                sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                sb.append("<decision-table>\n");

                // Criteria 列定义
                for (int c = 0; c < numCols; c++) {
                    sb.append(String.format(
                            "  <col type=\"Criteria\" num=\"%d\" var=\"var%d\" var-label=\"Var%d\" datatype=\"Integer\"/>\n",
                            c, c, c));
                }

                // row 元素（决定 maxRow）
                for (int r = 0; r < numRows; r++) {
                    sb.append(String.format("  <row num=\"%d\"/>\n", r));
                }

                // cell 元素
                int filledCount = 0;
                for (int r = 0; r < numRows; r++) {
                    for (int c = 0; c < numCols; c++) {
                        boolean filled = fills.get(r * numCols + c);
                        if (filled) {
                            sb.append(String.format(
                                    "  <cell row=\"%d\" col=\"%d\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"%d\"/></condition></joint></cell>\n",
                                    r, c, r * 10 + c));
                            filledCount++;
                        }
                        // 不填充 = 该 cell 不存在，解析时跳过
                    }
                }

                sb.append("</decision-table>\n");
                return Tuple.of(sb.toString(), filledCount);
            });
        });
    }

    // =====================================================================
    // Property 5: 决策树解析——约束组数等于叶节点数
    // =====================================================================

    @Property(tries = 100)
    void decisionTreeConstraintGroupsEqualLeafCount(
            @ForAll("decisionTreeSpec") Tuple2<String, Integer> spec
    ) throws Exception {
        String xml = spec.get1();
        int expectedLeafCount = spec.get2();

        Element root = parseXml(xml);
        List<ConditionConstraint> constraints = parser.parseDecisionTree(root, "test.dt.xml");

        assertEquals(expectedLeafCount, constraints.size(),
                "单层决策树：总约束数应等于叶节点(action-tree-node)数量");
    }

    @Provide
    Arbitrary<Tuple2<String, Integer>> decisionTreeSpec() {
        return Arbitraries.integers().between(1, 8).map(numBranches -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-tree>\n");
            sb.append("  <variable-tree-node>\n");
            sb.append("    <left var=\"score\" var-label=\"Score\" datatype=\"Integer\"/>\n");

            for (int i = 0; i < numBranches; i++) {
                sb.append(String.format(
                        "    <condition-tree-node op=\"GreaterThen\"><value content=\"%d\"/><action-tree-node/></condition-tree-node>\n",
                        i * 10));
            }

            sb.append("  </variable-tree-node>\n");
            sb.append("</decision-tree>\n");
            return Tuple.of(sb.toString(), numBranches);
        });
    }

    @Property(tries = 100)
    void decisionTreeMultiLevelConstraintGroupsEqualLeafCount(
            @ForAll("multiLevelTreeSpec") Tuple2<String, TreeStats> spec
    ) throws Exception {
        String xml = spec.get1();
        TreeStats stats = spec.get2();

        Element root = parseXml(xml);
        List<ConditionConstraint> constraints = parser.parseDecisionTree(root, "test.dt.xml");

        assertEquals(stats.totalConstraints, constraints.size(),
                "多层决策树：总约束数应等于所有路径深度之和");
    }

    /** 辅助类：记录树的统计信息 */
    static class TreeStats {
        int leafCount;
        int totalConstraints;

        TreeStats(int leafCount, int totalConstraints) {
            this.leafCount = leafCount;
            this.totalConstraints = totalConstraints;
        }
    }

    @Provide
    Arbitrary<Tuple2<String, TreeStats>> multiLevelTreeSpec() {
        Arbitrary<Integer> level1Branches = Arbitraries.integers().between(1, 4);
        Arbitrary<Integer> level2Branches = Arbitraries.integers().between(1, 3);

        return Combinators.combine(level1Branches, level2Branches).as((b1, b2) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-tree>\n");
            sb.append("  <variable-tree-node>\n");
            sb.append("    <left var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");

            int leafCount = 0;
            int totalConstraints = 0;

            for (int i = 0; i < b1; i++) {
                sb.append(String.format("    <condition-tree-node op=\"GreaterThen\"><value content=\"%d\"/>\n", i * 100));
                sb.append("      <variable-tree-node>\n");
                sb.append("        <left var=\"y\" var-label=\"Y\" datatype=\"Integer\"/>\n");
                for (int j = 0; j < b2; j++) {
                    sb.append(String.format(
                            "        <condition-tree-node op=\"LessThen\"><value content=\"%d\"/><action-tree-node/></condition-tree-node>\n",
                            j * 10));
                    leafCount++;
                    totalConstraints += 2;
                }
                sb.append("      </variable-tree-node>\n");
                sb.append("    </condition-tree-node>\n");
            }

            sb.append("  </variable-tree-node>\n");
            sb.append("</decision-tree>\n");
            return Tuple.of(sb.toString(), new TreeStats(leafCount, totalConstraints));
        });
    }

    // =====================================================================
    // Property 6: 评分卡解析——约束数等于有条件的行数
    // =====================================================================

    @Property(tries = 100)
    void scorecardConstraintCountEqualsConditionRowCount(
            @ForAll("scorecardSpec") Tuple2<String, Integer> spec
    ) throws Exception {
        String xml = spec.get1();
        int expectedConstraints = spec.get2();

        Element root = parseXml(xml);
        List<ConditionConstraint> constraints = parser.parseScorecard(root, "test.sc.xml");

        assertEquals(expectedConstraints, constraints.size(),
                "评分卡约束数应等于 condition cell 的总数");
    }

    @Provide
    Arbitrary<Tuple2<String, Integer>> scorecardSpec() {
        Arbitrary<Integer> rowCounts = Arbitraries.integers().between(1, 6);

        return rowCounts.flatMap(numRows ->
                Arbitraries.of(true, false).list().ofSize(numRows).map(hasConditions -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    sb.append("<scorecard scoring-type=\"sum\">\n");

                    int conditionCount = 0;
                    for (int r = 0; r < numRows; r++) {
                        sb.append(String.format(
                                "  <card-cell type=\"attribute\" row=\"%d\" col=\"0\" var=\"var%d\" var-label=\"Var%d\" datatype=\"Integer\"/>\n",
                                r, r, r));

                        if (hasConditions.get(r)) {
                            sb.append(String.format(
                                    "  <card-cell type=\"condition\" row=\"%d\" col=\"1\"><joint type=\"and\"><condition op=\"GreaterThen\"><value content=\"%d\"/></condition></joint></card-cell>\n",
                                    r, r * 10));
                            conditionCount++;
                        }
                    }

                    sb.append("</scorecard>\n");
                    return Tuple.of(sb.toString(), conditionCount);
                })
        );
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    private static Element parseXml(String xml) throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getRootElement();
    }
}
