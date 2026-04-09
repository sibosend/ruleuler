package com.caritasem.ruleuler.server.approval;

import com.bstek.urule.console.repository.model.ResourceItem;
import com.caritasem.ruleuler.server.approval.model.ApprovalDiffItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 将当前知识包的 ResourceItem 列表与上次发布快照对比，生成组件级变更摘要。
 */
@Component
@RequiredArgsConstructor
public class DiffCalculator {

    private final JdbcTemplate jdbc;

    private static final Map<String, String> SUFFIX_TYPE_MAP = new LinkedHashMap<>();

    static {
        SUFFIX_TYPE_MAP.put("vl.xml", "变量库");
        SUFFIX_TYPE_MAP.put("cl.xml", "常量库");
        SUFFIX_TYPE_MAP.put("pl.xml", "参数库");
        SUFFIX_TYPE_MAP.put("al.xml", "动作库");
        SUFFIX_TYPE_MAP.put("rs.xml", "规则集");
        SUFFIX_TYPE_MAP.put("rea.xml", "REA规则");
        SUFFIX_TYPE_MAP.put("ul", "脚本规则");
        SUFFIX_TYPE_MAP.put("dt.xml", "决策表");
        SUFFIX_TYPE_MAP.put("dts.xml", "脚本决策表");
        SUFFIX_TYPE_MAP.put("dtree.xml", "决策树");
        SUFFIX_TYPE_MAP.put("rl.xml", "决策流");
        SUFFIX_TYPE_MAP.put("sc", "评分卡");
    }

    /**
     * 计算当前 ResourceItem 列表相对于上次发布快照的变更。
     *
     * @param currentItems   当前知识包的决策组件列表
     * @param lastSnapshot   上次发布快照 {path → version_name}，null 表示首次发布
     * @return 变更列表（不含无变更项）
     */
    public List<ApprovalDiffItem> calculate(List<ResourceItem> currentItems,
                                             Map<String, String> lastSnapshot) {
        List<ApprovalDiffItem> diffs = new ArrayList<>();

        // 快照为 null = 首次发布，全部标记 ADDED
        if (lastSnapshot == null || lastSnapshot.isEmpty()) {
            for (ResourceItem item : currentItems) {
                String resolved = resolveVersion(item.getPath(), item.getVersion());
                diffs.add(ApprovalDiffItem.builder()
                        .componentPath(item.getPath())
                        .componentName(item.getName())
                        .componentType(resolveComponentType(item.getPath()))
                        .changeType("ADDED")
                        .prevVersion(null)
                        .currVersion(resolved)
                        .build());
            }
            return diffs;
        }

        Set<String> snapshotPaths = new HashSet<>(lastSnapshot.keySet());

        for (ResourceItem item : currentItems) {
            String resolved = resolveVersion(item.getPath(), item.getVersion());
            String prevVersion = lastSnapshot.get(item.getPath());

            if (prevVersion == null) {
                // 快照中没有 = 新增
                diffs.add(ApprovalDiffItem.builder()
                        .componentPath(item.getPath())
                        .componentName(item.getName())
                        .componentType(resolveComponentType(item.getPath()))
                        .changeType("ADDED")
                        .prevVersion(null)
                        .currVersion(resolved)
                        .build());
            } else {
                snapshotPaths.remove(item.getPath());
                if (!prevVersion.equals(resolved)) {
                    // 版本不同 = 修改
                    diffs.add(ApprovalDiffItem.builder()
                            .componentPath(item.getPath())
                            .componentName(item.getName())
                            .componentType(resolveComponentType(item.getPath()))
                            .changeType("MODIFIED")
                            .prevVersion(prevVersion)
                            .currVersion(resolved)
                            .build());
                }
                // 版本相同 = 无变更，不出现在 diff 中
            }
        }

        // 快照中有但当前没有 = 已删除
        for (String path : snapshotPaths) {
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            diffs.add(ApprovalDiffItem.builder()
                    .componentPath(path)
                    .componentName(name)
                    .componentType(resolveComponentType(path))
                    .changeType("DELETED")
                    .prevVersion(lastSnapshot.get(path))
                    .currVersion(null)
                    .build());
        }

        return diffs;
    }

    /**
     * 解析 LATEST 为实际版本号。非 LATEST 直接返回。
     * LATEST 查 ruleuler_rule_file_version 表最大版本；无版本记录返回 "V0"。
     */
    public String resolveVersion(String path, String versionRef) {
        if (versionRef == null || !"LATEST".equalsIgnoreCase(versionRef)) {
            return versionRef;
        }
        try {
            String versionName = jdbc.queryForObject(
                    "SELECT v.version_name FROM ruleuler_rule_file f " +
                    "JOIN ruleuler_rule_file_version v ON f.id = v.file_id " +
                    "WHERE f.path = ? ORDER BY v.version DESC LIMIT 1",
                    String.class, path);
            return versionName != null ? versionName : "V0";
        } catch (Exception e) {
            return "V0";
        }
    }

    /**
     * 根据文件后缀识别组件类型。
     */
    public static String resolveComponentType(String path) {
        if (path == null) return "未知";
        String lower = path.toLowerCase();
        for (Map.Entry<String, String> entry : SUFFIX_TYPE_MAP.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "未知";
    }
}
