package com.bstek.urule.model.library.variable;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.parse.VariableCategoryParser;
import com.bstek.urule.parse.VariableParser;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.util.List;
import java.util.Objects;

/**
 * Feature: general-entity-parameter, Property 1: VariableCategory 序列化 round-trip
 *
 * For any 合法的 VariableCategory（type 为 Custom、Clazz 或 GeneralEntity），
 * 序列化为 XML 再通过 VariableCategoryParser 反序列化，应产生等价的对象。
 *
 * Validates: Requirements 1.2, 1.3, 3.3, 3.4, 5.4
 */
class VariableCategoryRoundTripPropertyTest {

    private final VariableCategoryParser parser;

    VariableCategoryRoundTripPropertyTest() {
        parser = new VariableCategoryParser();
        parser.setVariableParser(new VariableParser());
    }

    @Property(tries = 200)
    void roundTrip(@ForAll("variableCategories") VariableCategory original) {
        Element xml = serializeToXml(original);
        VariableCategory parsed = parser.parse(xml);

        // category 级别等价
        assert Objects.equals(original.getName(), parsed.getName())
                : "name mismatch: " + original.getName() + " vs " + parsed.getName();
        assert original.getType() == parsed.getType()
                : "type mismatch: " + original.getType() + " vs " + parsed.getType();
        assert Objects.equals(original.getClazz(), parsed.getClazz())
                : "clazz mismatch: " + original.getClazz() + " vs " + parsed.getClazz();

        // variables 列表等价
        List<Variable> origVars = original.getVariables();
        List<Variable> parsedVars = parsed.getVariables();
        assert origVars.size() == parsedVars.size()
                : "variables size mismatch: " + origVars.size() + " vs " + parsedVars.size();

        for (int i = 0; i < origVars.size(); i++) {
            Variable ov = origVars.get(i);
            Variable pv = parsedVars.get(i);
            assert Objects.equals(ov.getName(), pv.getName())
                    : "var[" + i + "].name mismatch";
            assert Objects.equals(ov.getLabel(), pv.getLabel())
                    : "var[" + i + "].label mismatch";
            assert ov.getType() == pv.getType()
                    : "var[" + i + "].type mismatch";
            assert ov.getAct() == pv.getAct()
                    : "var[" + i + "].act mismatch";
            assert Objects.equals(ov.getDefaultValue(), pv.getDefaultValue())
                    : "var[" + i + "].defaultValue mismatch";
        }
    }

    // --- 序列化：VariableCategory → dom4j Element ---

    private Element serializeToXml(VariableCategory cat) {
        Element el = DocumentHelper.createElement("category");
        el.addAttribute("name", cat.getName());
        el.addAttribute("type", cat.getType().name());
        el.addAttribute("clazz", cat.getClazz());
        for (Variable v : cat.getVariables()) {
            Element varEl = el.addElement("var");
            varEl.addAttribute("name", v.getName());
            varEl.addAttribute("label", v.getLabel());
            varEl.addAttribute("type", v.getType().name());
            varEl.addAttribute("act", v.getAct().name());
            if (v.getDefaultValue() != null) {
                varEl.addAttribute("default-value", v.getDefaultValue());
            }
        }
        return el;
    }

    // --- jqwik Arbitrary 生成器 ---

    @Provide
    Arbitrary<VariableCategory> variableCategories() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<CategoryType> types = Arbitraries.of(CategoryType.values());
        Arbitrary<String> clazzes = Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('A', 'Z').withChars('.')
                .ofMinLength(3).ofMaxLength(50);
        Arbitrary<List<Variable>> varLists = variables().list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(names, types, clazzes, varLists).as((name, type, clazz, vars) -> {
            VariableCategory cat = new VariableCategory();
            cat.setName(name);
            cat.setType(type);
            cat.setClazz(clazz);
            cat.setVariables(vars);
            return cat;
        });
    }

    @Provide
    Arbitrary<Variable> variables() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> labels = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<Datatype> types = Arbitraries.of(Datatype.values());
        Arbitrary<Act> acts = Arbitraries.of(Act.values());

        return Combinators.combine(names, labels, types, acts).as((name, label, type, act) -> {
            Variable v = new Variable();
            v.setName(name);
            v.setLabel(label);
            v.setType(type);
            v.setAct(act);
            // defaultValue 留 null，round-trip 中 null 不会写入 XML 属性
            return v;
        });
    }
}
