package com.bstek.urule.model.library.variable;

import com.bstek.urule.builder.resource.ParameterLibraryResourceBuilder;
import com.bstek.urule.parse.ParameterLibraryParser;
import com.bstek.urule.parse.VariableParser;
import com.bstek.urule.parse.deserializer.ParameterLibraryDeserializer;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：CategoryType 枚举值检查 + ParameterLibraryResourceBuilder 类型不变
 * Requirements: 1.1, 1.4
 */
class CategoryTypeTest {

    @Test
    void categoryType_shouldContainExactlyThreeValues() {
        // Requirements 1.1: CategoryType 枚举包含 Custom、Clazz、GeneralEntity
        Set<String> expected = Set.of("Custom", "Clazz", "GeneralEntity");
        Set<String> actual = Arrays.stream(CategoryType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test
    void parameterLibraryResourceBuilder_shouldReturnClazzType() {
        // Requirements 1.4: ParameterLibraryResourceBuilder 保持使用 CategoryType.Clazz
        VariableParser variableParser = new VariableParser();
        ParameterLibraryParser libraryParser = new ParameterLibraryParser();
        libraryParser.setVariableParser(variableParser);
        ParameterLibraryDeserializer deserializer = new ParameterLibraryDeserializer();
        deserializer.setParameterLibraryParser(libraryParser);
        ParameterLibraryResourceBuilder builder = new ParameterLibraryResourceBuilder();
        builder.setParameterLibraryDeserializer(deserializer);

        Element root = DocumentHelper.createElement("parameter-library");
        VariableCategory category = builder.build(root);

        assertEquals(CategoryType.Clazz, category.getType());
    }
}
