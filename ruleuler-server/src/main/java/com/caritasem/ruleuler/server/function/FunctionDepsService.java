package com.caritasem.ruleuler.server.function;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FunctionDepsService {

    private final FunctionDepsDao depsDao;
    private final FunctionJarDao jarDao;

    public FunctionDepsService(FunctionDepsDao depsDao, FunctionJarDao jarDao) {
        this.depsDao = depsDao;
        this.jarDao = jarDao;
    }

    public void writeOnPublish(String project, String packageId, Map<String, String> snapshotContent) {
        List<Map<String, String>> deps = new ArrayList<>();
        if (snapshotContent != null) {
            for (Map.Entry<String, String> e : snapshotContent.entrySet()) {
                if (e.getKey() == null || !e.getKey().endsWith(".al.xml") || e.getValue() == null) {
                    continue;
                }
                Map<String, String> dep = readDep(project, e.getValue());
                if (dep != null) {
                    deps.add(dep);
                }
            }
        }
        depsDao.replace(project, packageId, deps);
    }

    public List<Map<String, Object>> depsForPackage(String packageId) {
        String[] parts = splitPackageId(packageId);
        return depsDao.findByPackage(parts[0], parts[1]);
    }

    public List<Map<String, Object>> allCurrentJars() {
        List<Map<String, Object>> rows = depsDao.findAllCurrent();
        Map<String, Map<String, Object>> uniq = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get("function_package") + "@" + row.get("version");
            uniq.putIfAbsent(key, row);
        }
        return new ArrayList<>(uniq.values());
    }

    private Map<String, String> readDep(String project, String xml) {
        try {
            Document doc = DocumentHelper.parseText(xml);
            Element root = doc.getRootElement();
            if (root == null) {
                return null;
            }
            String functionPackage = root.attributeValue("function-package");
            String version = root.attributeValue("function-version");
            if (functionPackage == null || functionPackage.isBlank()) {
                return null;
            }
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("动作库有 function-package 但缺少 function-version");
            }
            Map<String, Object> jar = jarDao.find(project, functionPackage, version)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "未上传函数包 " + functionPackage + ":" + version + "，无法发布"));
            return Map.of(
                    "functionPackage", functionPackage,
                    "version", version,
                    "checksum", String.valueOf(jar.get("checksum")));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析动作库失败: " + e.getMessage());
        }
    }

    static String[] splitPackageId(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            throw new IllegalArgumentException("packageId 必填");
        }
        String id = packageId.startsWith("/") ? packageId.substring(1) : packageId;
        int slash = id.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("packageId 格式必须是 project/package");
        }
        return new String[]{id.substring(0, slash), id.substring(slash + 1)};
    }
}
