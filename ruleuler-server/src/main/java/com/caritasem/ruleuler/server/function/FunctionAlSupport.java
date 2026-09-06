package com.caritasem.ruleuler.server.function;

import com.bstek.urule.model.library.action.ActionLibrary;
import com.bstek.urule.model.library.action.Method;
import com.bstek.urule.model.library.action.Parameter;
import com.bstek.urule.model.library.action.SpringBean;
import com.bstek.urule.parse.ActionLibraryParser;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FunctionAlSupport {

    static final Pattern PACKAGE_NAME = Pattern.compile("[a-zA-Z0-9_-]+");
    static final Set<String> PARAM_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "BigDecimal", "Boolean", "Date", "List", "Map");

    private final JdbcTemplate jdbc;
    private final ActionLibraryParser parser = new ActionLibraryParser();

    public FunctionAlSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ParsedAl parse(String xml) {
        Document doc;
        try {
            doc = DocumentHelper.parseText(xml);
        } catch (DocumentException e) {
            throw new IllegalArgumentException("functions.al.xml 无法解析: " + e.getMessage());
        }
        Element root = doc.getRootElement();
        if (root == null || !"action-library".equals(root.getName())) {
            throw new IllegalArgumentException("根节点必须是 action-library");
        }
        String functionPackage = root.attributeValue("function-package");
        String functionVersion = root.attributeValue("function-version");
        if (functionPackage == null || functionPackage.isBlank()
                || functionVersion == null || functionVersion.isBlank()) {
            throw new IllegalArgumentException("缺少 function-package / function-version");
        }
        if (!PACKAGE_NAME.matcher(functionPackage).matches()) {
            throw new IllegalArgumentException("function-package 只许 [a-zA-Z0-9_-]+");
        }
        ActionLibrary lib;
        try {
            lib = parser.parse(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("动作库解析失败: " + e.getMessage());
        }
        if (lib.getSpringBeans() == null || lib.getSpringBeans().isEmpty()) {
            throw new IllegalArgumentException("动作库没有 spring-bean");
        }
        Set<String> beanIds = new HashSet<>();
        for (SpringBean bean : lib.getSpringBeans()) {
            if (bean.getId() == null || bean.getId().isBlank()) {
                throw new IllegalArgumentException("spring-bean 缺少 id");
            }
            if (!beanIds.add(bean.getId())) {
                throw new IllegalArgumentException("同一包内 beanId 重复: " + bean.getId());
            }
            if (bean.getMethods() == null) {
                continue;
            }
            for (Method method : bean.getMethods()) {
                if (method.getParameters() == null) {
                    continue;
                }
                for (Parameter p : method.getParameters()) {
                    String type = p.getType() == null ? null : p.getType().name();
                    if (type == null || !PARAM_TYPES.contains(type)) {
                        throw new IllegalArgumentException("参数类型非法: " + type);
                    }
                }
            }
        }
        return new ParsedAl(functionPackage, functionVersion, beanIds, xml);
    }

    public void assertNoBeanIdClash(String project, String alPath, Set<String> beanIds) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT path, content FROM ruleuler_rule_file
                WHERE project=? AND is_dir=0 AND name LIKE '%.al.xml' AND path<>?
                """, project, alPath);
        for (Map<String, Object> row : rows) {
            String content = (String) row.get("content");
            if (content == null || content.isBlank()) {
                continue;
            }
            try {
                Document doc = DocumentHelper.parseText(content);
                ActionLibrary lib = parser.parse(doc.getRootElement());
                if (lib.getSpringBeans() == null) {
                    continue;
                }
                for (SpringBean bean : lib.getSpringBeans()) {
                    if (beanIds.contains(bean.getId())) {
                        throw new IllegalArgumentException(
                                "同项目 beanId 冲突: " + bean.getId() + " 已在 " + row.get("path"));
                    }
                }
            } catch (DocumentException ignored) {
                // 坏 XML 不是本包的责任
            }
        }
    }

    public record ParsedAl(String functionPackage, String functionVersion, Set<String> beanIds, String xml) {}
}
