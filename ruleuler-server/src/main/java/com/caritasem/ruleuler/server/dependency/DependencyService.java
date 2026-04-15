package com.caritasem.ruleuler.server.dependency;

import com.caritasem.ruleuler.server.approval.DiffCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 按需依赖分析：实时扫描项目文件，构建内存依赖图，返回结果。
 */
@Service
@RequiredArgsConstructor
public class DependencyService {

    private final JdbcTemplate jdbc;

    // ── 返回结构 ──

    public record DependencyNode(String path, String type, String refKind) {}

    public record AffectedPackage(String id, String name, String viaFlow) {}

    public record AnalysisResult(
            DependencyNode target,
            List<DependencyNode> dependencies,
            List<DependencyNode> referers,
            List<AffectedPackage> affectedPackages
    ) {}

    // ── 核心方法 ──

    public AnalysisResult analyze(String targetPath) {
        String project = extractProject(targetPath);
        String targetType = DiffCalculator.resolveComponentType(targetPath);

        // 1. 加载项目所有非目录文件
        List<FileEntry> files = loadProjectFiles(project);

        // 2. 提取所有引用，构建依赖图
        //    graph: sourcePath -> [targetPath]
        //    reverseGraph: targetPath -> [sourcePath]
        Map<String, List<DependencyExtractor.RefEntry>> graph = new LinkedHashMap<>();
        Map<String, List<String>> reverseGraph = new LinkedHashMap<>();
        Map<String, String> pathToType = new HashMap<>();

        for (FileEntry f : files) {
            List<DependencyExtractor.RefEntry> refs = DependencyExtractor.extract(f.path, f.content);
            if (!refs.isEmpty()) {
                graph.put(f.path, refs);
            }
            pathToType.put(f.path, DiffCalculator.resolveComponentType(f.path));

            for (DependencyExtractor.RefEntry ref : refs) {
                reverseGraph.computeIfAbsent(ref.targetPath(), k -> new ArrayList<>()).add(f.path);
            }
        }

        // 3. 正向：targetPath 依赖了谁
        List<DependencyNode> dependencies = new ArrayList<>();
        List<DependencyExtractor.RefEntry> outRefs = graph.getOrDefault(targetPath, List.of());
        for (DependencyExtractor.RefEntry ref : outRefs) {
            String t = pathToType.getOrDefault(ref.targetPath(), DiffCalculator.resolveComponentType(ref.targetPath()));
            dependencies.add(new DependencyNode(ref.targetPath(), t, ref.refKind()));
        }

        // 4. 反向：谁依赖了 targetPath
        List<DependencyNode> referers = new ArrayList<>();
        List<String> inSources = reverseGraph.getOrDefault(targetPath, List.of());
        for (String srcPath : inSources) {
            String srcType = pathToType.getOrDefault(srcPath, DiffCalculator.resolveComponentType(srcPath));
            // 找到这个 source 指向 target 的 refKind
            String refKind = findRefKind(graph, srcPath, targetPath);
            referers.add(new DependencyNode(srcPath, srcType, refKind));
        }

        // 5. 受影响的知识包
        List<AffectedPackage> affectedPackages = new ArrayList<>();
        Set<String> refererFlows = new HashSet<>();
        for (DependencyNode r : referers) {
            if (r.path().endsWith(".rl.xml")) {
                refererFlows.add(r.path());
            }
        }
        if (!refererFlows.isEmpty()) {
            String pkgContent = loadPackageFile(project);
            if (pkgContent != null) {
                List<DependencyExtractor.RefEntry> pkgRefs = DependencyExtractor.extract(
                        "/" + project + "/___res__package__file__", pkgContent);
                // 解析知识包 id/name
                Map<String, String[]> pkgInfo = parsePackageInfo(pkgContent);
                for (DependencyExtractor.RefEntry pkgRef : pkgRefs) {
                    if (refererFlows.contains(pkgRef.targetPath())) {
                        String[] info = pkgInfo.get(pkgRef.targetPath());
                        affectedPackages.add(new AffectedPackage(
                                info != null ? info[0] : "",
                                info != null ? info[1] : "",
                                pkgRef.targetPath()
                        ));
                    }
                }
            }
        }

        return new AnalysisResult(
                new DependencyNode(targetPath, targetType, null),
                dependencies, referers, affectedPackages
        );
    }

    // ── 内部方法 ──

    private record FileEntry(String path, String content) {}

    private List<FileEntry> loadProjectFiles(String project) {
        return jdbc.query(
                "SELECT path, content FROM ruleuler_rule_file WHERE project=? AND is_dir=0",
                (rs, rowNum) -> new FileEntry(rs.getString("path"), rs.getString("content")),
                project);
    }

    private String loadPackageFile(String project) {
        try {
            return jdbc.queryForObject(
                    "SELECT content FROM ruleuler_rule_file WHERE path=?",
                    String.class, "/" + project + "/___res__package__file__");
        } catch (Exception e) {
            return null;
        }
    }

    private String findRefKind(Map<String, List<DependencyExtractor.RefEntry>> graph, String srcPath, String targetPath) {
        List<DependencyExtractor.RefEntry> refs = graph.get(srcPath);
        if (refs == null) return "unknown";
        for (DependencyExtractor.RefEntry ref : refs) {
            if (ref.targetPath().equals(targetPath)) return ref.refKind();
        }
        return "unknown";
    }

    /** 解析知识包 XML，返回 path -> [packageId, packageName] */
    private Map<String, String[]> parsePackageInfo(String pkgContent) {
        Map<String, String[]> result = new HashMap<>();
        try {
            var pattern = java.util.regex.Pattern.compile(
                    "<res-package\\s+id=\"([^\"]+)\"\\s+name=\"([^\"]+)\"[^>]*>(.*?)</res-package>",
                    java.util.regex.Pattern.DOTALL);
            var matcher = pattern.matcher(pkgContent);
            while (matcher.find()) {
                String pkgId = matcher.group(1);
                String pkgName = matcher.group(2);
                String body = matcher.group(3);
                var itemPattern = java.util.regex.Pattern.compile("path=\"dbr:([^\"]+)\"");
                var itemMatcher = itemPattern.matcher(body);
                while (itemMatcher.find()) {
                    result.put(itemMatcher.group(1), new String[]{pkgId, pkgName});
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String extractProject(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        int pos = path.indexOf("/");
        return pos == -1 ? path : path.substring(0, pos);
    }
}
