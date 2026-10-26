package com.bstek.urule.dsl;

import com.bstek.urule.builder.table.CellScriptDSLBuilder;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleParserGenerationTest {

    @Test
    void parsesEnglishRule() {
        assertParsesRule("rule \"adult\" if Person.age >= 18 then parameter.adult = true end");
    }

    @Test
    void parsesChineseRule() {
        assertParsesRule("规则 \"成年\" 如果 用户.年龄 大于等于 18 那么 参数.成年 = true 结束");
    }

    @Test
    void buildsDecisionTableCellConditions() {
        CellScriptDSLBuilder builder = new CellScriptDSLBuilder();

        String script = builder.buildCriteriaScript(">= 18 and < 65", "Person.age");

        assertEquals("Person.age >= 18 and Person.age < 65", script.trim().replaceAll("\\s+", " "));
    }

    private void assertParsesRule(String script) {
        RuleParserLexer lexer = new RuleParserLexer(new ANTLRInputStream(script));
        RuleParserParser parser = new RuleParserParser(new CommonTokenStream(lexer));
        parser.setErrorHandler(new BailErrorStrategy());

        RuleParserParser.RuleSetContext ruleSet = parser.ruleSet();

        assertEquals(1, ruleSet.ruleSetBody().rules().size());
        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals(Token.EOF, parser.getCurrentToken().getType());
    }
}
