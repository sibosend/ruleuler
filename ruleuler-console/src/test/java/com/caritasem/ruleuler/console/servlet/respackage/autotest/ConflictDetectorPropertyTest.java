package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: autotest-v2, Property 1: 决策表行重叠检测正确性
// Feature: autotest-v2, Property 2: 决策树分支完备性检测正确性
// Feature: autotest-v2, Property 3: 冲突结果完整性
// Validates: Requirements 1.2, 1.4, 1.6

import com.bstek.urule.model.library.Datatype;
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
 * ConflictDetector 属性测试。
 */
class ConflictDetectorPropertyTest {

    private final ConflictDetector detector = new ConflictDetector();

    @Property(tries = 100)
    void overlappingRowsShouldBeDetected(@ForAll("overlappingDecisionTableXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTableOverlap("test.dt.xml", root);
        assertTrue(conflicts.stream().anyMatch(c -> "OVERLAP".equals(c.getConflictType())),
                "两行条件完全相同时应报告 OVERLAP 冲突");
    }

    @Provide
    Arbitrary<String> overlappingDecisionTableXml() {
        return Arbitraries.integers().between(0, 1000).map(val -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-table>\n");
            sb.append("  <col type=\"Criteria\" num=\"0\" var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");
            sb.append("  <row num=\"0\"/>\n");
            sb.append("  <row num=\"1\"/>\n");
            sb.append(String.format("  <cell row=\"0\" col=\"0\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"%d\"/></condition></joint></cell>\n", val));
            sb.append(String.format("  <cell row=\"1\" col=\"0\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"%d\"/></condition></joint></cell>\n", val));
            sb.append("</decision-table>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void mutuallyExclusiveRowsShouldNotOverlap(@ForAll("mutuallyExclusiveDecisionTableXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTableOverlap("test.dt.xml", root);
        assertTrue(conflicts.stream().noneMatch(c -> "OVERLAP".equals(c.getConflictType())),
                "两行条件互斥时不应报告 OVERLAP 冲突");
    }

    @Provide
    Arbitrary<String> mutuallyExclusiveDecisionTableXml() {
        return Arbitraries.integers().between(0, 999).map(val -> {
            int val2 = val + 1;
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-table>\n");
            sb.append("  <col type=\"Criteria\" num=\"0\" var=\"x\" var-label=\"X\" datatype=\"String\"/>\n");
            sb.append("  <row num=\"0\"/>\n");
            sb.append("  <row num=\"1\"/>\n");
            sb.append(String.format("  <cell row=\"0\" col=\"0\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"%d\"/></condition></joint></cell>\n", val));
            sb.append(String.format("  <cell row=\"1\" col=\"0\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"%d\"/></condition></joint></cell>\n", val2));
            sb.append("</decision-table>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void numericOverlappingRangesShouldBeDetected(@ForAll("numericOverlappingTableXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTableOverlap("test.dt.xml", root);
        assertTrue(conflicts.stream().anyMatch(c -> "OVERLAP".equals(c.getConflictType())),
                "数值范围有交集时应报告 OVERLAP 冲突");
    }

    @Provide
    Arbitrary<String> numericOverlappingTableXml() {
        return Arbitraries.integers().between(0, 100).map(a -> {
            int b = a + 10;
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-table>\n");
            sb.append("  <col type=\"Criteria\" num=\"0\" var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");
            sb.append("  <row num=\"0\"/>\n");
            sb.append("  <row num=\"1\"/>\n");
            sb.append(String.format("  <cell row=\"0\" col=\"0\"><joint type=\"and\"><condition op=\"GreaterThen\"><value content=\"%d\"/></condition></joint></cell>\n", a));
            sb.append(String.format("  <cell row=\"1\" col=\"0\"><joint type=\"and\"><condition op=\"LessThen\"><value content=\"%d\"/></condition></joint></cell>\n", b));
            sb.append("</decision-table>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void numericNonOverlappingRangesShouldNotBeDetected(@ForAll("numericNonOverlappingTableXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTableOverlap("test.dt.xml", root);
        assertTrue(conflicts.stream().noneMatch(c -> "OVERLAP".equals(c.getConflictType())),
                "数值范围无交集时不应报告 OVERLAP 冲突");
    }

    @Provide
    Arbitrary<String> numericNonOverlappingTableXml() {
        return Arbitraries.integers().between(0, 100).map(a -> {
            int b = a + 10;
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<decision-table>\n");
            sb.append("  <col type=\"Criteria\" num=\"0\" var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");
            sb.append("  <row num=\"0\"/>\n");
            sb.append("  <row num=\"1\"/>\n");
            sb.append(String.format("  <cell row=\"0\" col=\"0\"><joint type=\"and\"><condition op=\"LessThen\"><value content=\"%d\"/></condition></joint></cell>\n", a));
            sb.append(String.format("  <cell row=\"1\" col=\"0\"><joint type=\"and\"><condition op=\"GreaterThen\"><value content=\"%d\"/></condition></joint></cell>\n", b));
            sb.append("</decision-table>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void completeBranchesShouldBeDetectedAsComplete(@ForAll("completeBranchPair") List<ConditionConstraint> branches) {
        assertTrue(detector.isNumericBranchesComplete(branches), "互补分支 (<= v, > v) 应判定为完备");
    }

    @Provide
    Arbitrary<List<ConditionConstraint>> completeBranchPair() {
        return Arbitraries.integers().between(-1000, 1000).map(v -> {
            ConditionConstraint cc1 = new ConditionConstraint();
            cc1.setVariableName("x"); cc1.setDatatype(Datatype.Integer); cc1.setOp(Op.LessThenEquals); cc1.setValue(String.valueOf(v));
            ConditionConstraint cc2 = new ConditionConstraint();
            cc2.setVariableName("x"); cc2.setDatatype(Datatype.Integer); cc2.setOp(Op.GreaterThen); cc2.setValue(String.valueOf(v));
            return List.of(cc1, cc2);
        });
    }

    @Property(tries = 100)
    void incompleteBranchesShouldBeDetectedAsIncomplete(@ForAll("incompleteBranch") List<ConditionConstraint> branches) {
        assertFalse(detector.isNumericBranchesComplete(branches), "只有单方向条件应判定为不完备");
    }

    @Provide
    Arbitrary<List<ConditionConstraint>> incompleteBranch() {
        return Arbitraries.integers().between(-1000, 1000).map(v -> {
            ConditionConstraint cc = new ConditionConstraint();
            cc.setVariableName("x"); cc.setDatatype(Datatype.Integer); cc.setOp(Op.GreaterThen); cc.setValue(String.valueOf(v));
            return List.of(cc);
        });
    }

    @Property(tries = 100)
    void completeTreeShouldNotReportIncomplete(@ForAll("completeTreeXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTreeCompleteness("test.dt.xml", root);
        assertTrue(conflicts.stream().noneMatch(c -> "INCOMPLETE".equals(c.getConflictType())), "完备分支不应报告 INCOMPLETE");
    }

    @Provide
    Arbitrary<String> completeTreeXml() {
        return Arbitraries.integers().between(-100, 100).map(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<decision-tree>\n  <variable-tree-node>\n    <left var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");
            sb.append(String.format("    <condition-tree-node op=\"LessThenEquals\"><value content=\"%d\"/><action-tree-node/></condition-tree-node>\n", v));
            sb.append(String.format("    <condition-tree-node op=\"GreaterThen\"><value content=\"%d\"/><action-tree-node/></condition-tree-node>\n", v));
            sb.append("  </variable-tree-node>\n</decision-tree>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void incompleteTreeShouldReportIncomplete(@ForAll("incompleteTreeXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTreeCompleteness("test.dt.xml", root);
        assertTrue(conflicts.stream().anyMatch(c -> "INCOMPLETE".equals(c.getConflictType())), "不完备分支应报告 INCOMPLETE");
    }

    @Provide
    Arbitrary<String> incompleteTreeXml() {
        return Arbitraries.integers().between(-100, 100).map(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<decision-tree>\n  <variable-tree-node>\n    <left var=\"x\" var-label=\"X\" datatype=\"Integer\"/>\n");
            sb.append(String.format("    <condition-tree-node op=\"GreaterThen\"><value content=\"%d\"/><action-tree-node/></condition-tree-node>\n", v));
            sb.append("  </variable-tree-node>\n</decision-tree>\n");
            return sb.toString();
        });
    }

    @Property(tries = 100)
    void multiCutPointCompleteBranchesShouldBeComplete(@ForAll("multiCutCompleteBranches") List<ConditionConstraint> branches) {
        assertTrue(detector.isNumericBranchesComplete(branches), "多切分点完备分支应判定为完备");
    }

    @Provide
    Arbitrary<List<ConditionConstraint>> multiCutCompleteBranches() {
        return Arbitraries.integers().between(-100, 100).map(a -> {
            int b = a + 10;
            ConditionConstraint cc1 = new ConditionConstraint(); cc1.setVariableName("x"); cc1.setDatatype(Datatype.Integer); cc1.setOp(Op.LessThen); cc1.setValue(String.valueOf(a));
            ConditionConstraint cc2 = new ConditionConstraint(); cc2.setVariableName("x"); cc2.setDatatype(Datatype.Integer); cc2.setOp(Op.GreaterThenEquals); cc2.setValue(String.valueOf(a));
            ConditionConstraint cc3 = new ConditionConstraint(); cc3.setVariableName("x"); cc3.setDatatype(Datatype.Integer); cc3.setOp(Op.LessThenEquals); cc3.setValue(String.valueOf(b));
            ConditionConstraint cc4 = new ConditionConstraint(); cc4.setVariableName("x"); cc4.setDatatype(Datatype.Integer); cc4.setOp(Op.GreaterThen); cc4.setValue(String.valueOf(b));
            return List.of(cc1, cc2, cc3, cc4);
        });
    }

    @Property(tries = 100)
    void overlapConflictItemFieldsAreValid(@ForAll("overlappingDecisionTableXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTableOverlap("test.dt.xml", root);
        for (ConflictItem item : conflicts) { assertValidConflictItem(item); }
    }

    @Property(tries = 100)
    void incompleteConflictItemFieldsAreValid(@ForAll("incompleteTreeXml") String xml) throws Exception {
        Element root = parseXml(xml);
        List<ConflictItem> conflicts = detector.detectDecisionTreeCompleteness("test.dt.xml", root);
        for (ConflictItem item : conflicts) { assertValidConflictItem(item); }
    }

    @Property(tries = 100)
    void randomConflictItemFieldConstraints(
            @ForAll("validConflictType") String conflictType, @ForAll("validSeverity") String severity,
            @ForAll("nonEmptyString") String ruleFile, @ForAll("nonEmptyString") String description) {
        ConflictItem item = new ConflictItem();
        item.setConflictType(conflictType); item.setSeverity(severity); item.setRuleFile(ruleFile); item.setDescription(description);
        assertValidConflictItem(item);
    }

    @Provide Arbitrary<String> validConflictType() { return Arbitraries.of("OVERLAP", "INCOMPLETE", "OVERRIDE"); }
    @Provide Arbitrary<String> validSeverity() { return Arbitraries.of("ERROR", "WARNING"); }
    @Provide Arbitrary<String> nonEmptyString() { return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50); }

    private static final Set<String> VALID_CONFLICT_TYPES = Set.of("OVERLAP", "INCOMPLETE", "OVERRIDE");
    private static final Set<String> VALID_SEVERITIES = Set.of("ERROR", "WARNING");

    private void assertValidConflictItem(ConflictItem item) {
        assertNotNull(item.getConflictType()); assertTrue(VALID_CONFLICT_TYPES.contains(item.getConflictType()));
        assertNotNull(item.getSeverity()); assertTrue(VALID_SEVERITIES.contains(item.getSeverity()));
        assertNotNull(item.getRuleFile()); assertFalse(item.getRuleFile().isEmpty());
        assertNotNull(item.getDescription()); assertFalse(item.getDescription().isEmpty());
    }

    private static Element parseXml(String xml) throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getRootElement();
    }
}
