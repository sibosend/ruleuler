package com.caritasem.ruleuler.server.dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从规则文件 XML 内容中提取 dbr: 引用关系。
 */
public class DependencyExtractor {

    /** 库引用：import-variable-library / import-parameter-library / import-constant-library / import-action-library */
    private static final Pattern LIBRARY_PATTERN = Pattern.compile(
            "<import-\\w+-library\\s+path=\"dbr:([^\"]+)\"");

    /** 决策流中 rule 节点的 file 引用 */
    private static final Pattern FILE_REF_PATTERN = Pattern.compile(
            "file=\"dbr:([^\"]+)\"");

    /** 知识包中 res-package-item 的 path 引用 */
    private static final Pattern PACKAGE_ITEM_PATTERN = Pattern.compile(
            "<res-package-item\\s+[^>]*path=\"dbr:([^\"]+)\"");

    public record RefEntry(String targetPath, String refKind) {}

    /**
     * 从文件内容中提取所有 dbr: 引用。
     * 根据文件类型选择不同的提取策略。
     */
    public static List<RefEntry> extract(String path, String content) {
        if (content == null || content.isEmpty()) return List.of();

        List<RefEntry> refs = new ArrayList<>();

        // 知识包文件：只提取 package-item 引用
        if (path.contains("___res__package__file__")) {
            matchAll(PACKAGE_ITEM_PATTERN, content, "package_item", refs);
            return refs;
        }

        // 决策流：提取库引用 + file 引用
        if (path.endsWith(".rl.xml")) {
            matchAll(LIBRARY_PATTERN, content, "library", refs);
            matchAll(FILE_REF_PATTERN, content, "file", refs);
            return refs;
        }

        // 其他决策组件：只提取库引用
        matchAll(LIBRARY_PATTERN, content, "library", refs);
        return refs;
    }

    private static void matchAll(Pattern pattern, String content, String refKind, List<RefEntry> result) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            result.add(new RefEntry(matcher.group(1), refKind));
        }
    }
}
