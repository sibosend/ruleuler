package com.caritasem.ruleuler.server.approval;

import com.caritasem.ruleuler.server.approval.model.ApprovalDiffItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.*;

/**
 * 基于内容（XML）的 diff 计算：对比项目资源当前内容与上次快照内容，
 * 生成组件级变更摘要，并解析到具体规则行级变动。
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
     * 加载项目下所有决策资源的当前内容。
     * 返回 dbPath → xmlContent
     */
    public Map<String, String> loadProjectContentMap(String project) {
        Map<String, String> map = new LinkedHashMap<>();
        jdbc.query(
                "SELECT path, content FROM ruleuler_rule_file WHERE path LIKE ? AND is_dir = 0",
                rs -> {
                    String path = rs.getString("path");
                    if (!"未知".equals(resolveComponentType(path))) {
                        map.put(path, rs.getString("content") != null ? rs.getString("content") : "");
                    }
                },
                "/" + project + "/%");
        return map;
    }

    /**
     * 基于内容对比计算变更。
     *
     * @param currentMap  当前资源内容 {dbPath → xmlContent}
     * @param prevMap     上次快照内容 {dbPath → xmlContent}，null 表示首次提交
     * @return 变更列表
     */
    public List<ApprovalDiffItem> calculateContentDiff(Map<String, String> currentMap,
                                                       Map<String, String> prevMap) {
        List<ApprovalDiffItem> diffs = new ArrayList<>();

        if (prevMap == null || prevMap.isEmpty()) {
            // 首次提交，全部 ADDED
            for (Map.Entry<String, String> e : currentMap.entrySet()) {
                diffs.add(buildItem(e.getKey(), "ADDED", null, e.getValue()));
            }
            return diffs;
        }

        Set<String> prevPaths = new HashSet<>(prevMap.keySet());

        for (Map.Entry<String, String> e : currentMap.entrySet()) {
            String path = e.getKey();
            String curr = e.getValue();
            String prev = prevMap.get(path);

            if (prev == null) {
                diffs.add(buildItem(path, "ADDED", null, curr));
            } else {
                prevPaths.remove(path);
                if (!Objects.equals(prev, curr)) {
                    String details = computeRuleDiff(path, prev, curr);
                    diffs.add(buildItem(path, "MODIFIED", prev, curr).toBuilder()
                            .details(details).build());
                }
            }
        }

        for (String path : prevPaths) {
            diffs.add(buildItem(path, "DELETED", prevMap.get(path), null));
        }

        return diffs;
    }

    private ApprovalDiffItem buildItem(String dbPath, String changeType, String prevContent, String currContent) {
        String name = dbPath.contains("/") ? dbPath.substring(dbPath.lastIndexOf('/') + 1) : dbPath;
        return ApprovalDiffItem.builder()
                .componentPath("dbr:" + dbPath)
                .componentName(name)
                .componentType(resolveComponentType(dbPath))
                .changeType(changeType)
                .prevVersion(prevContent != null ? Integer.toHexString(prevContent.hashCode()) : null)
                .currVersion(currContent != null ? Integer.toHexString(currContent.hashCode()) : null)
                .build();
    }

    /**
     * 解析 XML 内容，提取规则级行级变动。
     * 返回 JSON 数组，如 [{"rule":"行3","change":"MODIFIED","prev":"...","curr":"..."}]
     */
    String computeRuleDiff(String path, String prevXml, String currXml) {
        try {
            Map<String, String> prevRules = extractRules(path, prevXml);
            Map<String, String> currRules = extractRules(path, currXml);
            return buildRuleDiffJson(prevRules, currRules);
        } catch (Exception e) {
            // XML 解析失败，返回整体变更摘要
            return "[{\"rule\":\"整体\",\"change\":\"MODIFIED\"}]";
        }
    }

    /**
     * 从 XML 中提取规则级元素。
     * 按资源类型采用不同策略：
     * - 决策表/脚本决策表：按 cell 的 row 属性分组
     * - 规则集：按 rule 的 name 属性
     * - REA规则：按 rea-item 的 name 属性
     * - 决策流：按 node 的 name 属性
     * - 决策树：按 node 的 var/label
     * - 其他：按顶层子元素 tag+索引
     */
    Map<String, String> extractRules(String path, String xml) throws Exception {
        if (xml == null || xml.isBlank()) return Collections.emptyMap();

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        Element root = doc.getDocumentElement();
        String lower = path.toLowerCase();

        if (lower.endsWith(".dt.xml") || lower.endsWith(".dts.xml")) {
            return extractByRow(root);
        }
        if (lower.endsWith(".rs.xml")) {
            return extractByAttr(root, "rule", "name");
        }
        if (lower.endsWith(".rea.xml")) {
            return extractByAttr(root, "rule", "name");
        }
        if (lower.endsWith(".rl.xml")) {
            return extractFlowNodes(root);
        }
        if (lower.endsWith(".dtree.xml")) {
            return extractDecisionTreeRules(root);
        }
        // 变量库、参数库、评分卡等：按顶层子元素
        return extractByTopChildren(root);
    }

    /** 决策表：按 row 分组所有 cell */
    private Map<String, String> extractByRow(Element root) throws Exception {
        Map<String, String> rules = new LinkedHashMap<>();
        // 收集所有 cell，按 row 分组
        Map<String, List<Element>> rowCells = new TreeMap<>((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (NumberFormatException e) { return a.compareTo(b); }
        });

        NodeList cells = root.getElementsByTagName("cell");
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            String row = cell.getAttribute("row");
            rowCells.computeIfAbsent(row, k -> new ArrayList<>()).add(cell);
        }

        for (Map.Entry<String, List<Element>> e : rowCells.entrySet()) {
            StringBuilder sb = new StringBuilder("<row>");
            for (Element cell : e.getValue()) {
                sb.append(elementToString(cell)).append("\n");
            }
            sb.append("</row>");
            rules.put("行" + e.getKey(), sb.toString());
        }
        return rules;
    }

    /** 决策流：提取 <rule>/<start>/<end> 节点，按 name 属性分组 */
    private Map<String, String> extractFlowNodes(Element root) throws Exception {
        Map<String, String> rules = new LinkedHashMap<>();
        String[] tags = {"rule", "start", "end"};
        for (String tag : tags) {
            NodeList nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String name = el.getAttribute("name");
                if (name == null || name.isEmpty()) name = tag + "#" + i;
                rules.put(name, elementToString(el));
            }
        }
        if (rules.isEmpty()) {
            return extractByTopChildren(root);
        }
        return rules;
    }

    /** 按子元素 tag 的某个属性分组 */
    private Map<String, String> extractByAttr(Element root, String tagName, String attrName) throws Exception {
        Map<String, String> rules = new LinkedHashMap<>();
        NodeList nodes = root.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = el.getAttribute(attrName);
            if (name == null || name.isEmpty()) name = tagName + "#" + i;
            rules.put(name, elementToString(el));
        }
        if (rules.isEmpty()) {
            // 如果没有找到对应 tag，按顶层子元素兜底
            return extractByTopChildren(root);
        }
        return rules;
    }

    /** 决策树：node 按 var 属性或顺序 */
    private Map<String, String> extractDecisionTreeRules(Element root) throws Exception {
        Map<String, String> rules = new LinkedHashMap<>();
        NodeList nodes = root.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String var = el.getAttribute("var");
            String label = el.getAttribute("var-label");
            String key = (label != null && !label.isEmpty()) ? label
                    : (var != null && !var.isEmpty()) ? var : "节点" + i;
            rules.put(key, elementToString(el));
        }
        return rules;
    }

    /** 兜底：按顶层子元素 tag+索引 */
    private Map<String, String> extractByTopChildren(Element root) throws Exception {
        Map<String, String> rules = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        int idx = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el) {
                String tag = el.getTagName();
                // 尝试用 name 属性
                String name = el.getAttribute("name");
                String key = (name != null && !name.isEmpty()) ? name : tag + "#" + idx;
                rules.put(key, elementToString(el));
                idx++;
            }
        }
        return rules;
    }

    private String buildRuleDiffJson(Map<String, String> prevRules, Map<String, String> currRules) {
        List<String> parts = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(prevRules.keySet());
        allKeys.addAll(currRules.keySet());

        for (String key : allKeys) {
            String prev = prevRules.get(key);
            String curr = currRules.get(key);
            if (Objects.equals(prev, curr)) continue;

            String change = (prev == null) ? "ADDED" : (curr == null) ? "DELETED" : "MODIFIED";
            StringBuilder sb = new StringBuilder();
            sb.append("{\"rule\":\"").append(escapeJson(key))
              .append("\",\"change\":\"").append(change).append("\"");

            if ("MODIFIED".equals(change)) {
                // 提取具体字段级差异
                String fieldDiffs = extractFieldDiffs(prev, curr);
                if (fieldDiffs != null) {
                    sb.append(",\"fields\":").append(fieldDiffs);
                }
            }
            sb.append("}");
            parts.add(sb.toString());
        }
        return "[" + String.join(",", parts) + "]";
    }

    /**
     * 对比两段 XML，提取具体字段级差异。
     * 返回 JSON 数组如 [{"field":"arrival_time >=","prev":"6.8","curr":"6.9"}]
     */
    private String extractFieldDiffs(String prevXml, String currXml) {
        try {
            // 从两段 XML 中提取所有 "有意义的值"
            Map<String, String> prevFields = extractSemanticFields(prevXml);
            Map<String, String> currFields = extractSemanticFields(currXml);

            List<String> diffs = new ArrayList<>();
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(prevFields.keySet());
            allKeys.addAll(currFields.keySet());

            for (String field : allKeys) {
                String pv = prevFields.get(field);
                String cv = currFields.get(field);
                if (Objects.equals(pv, cv)) continue;

                StringBuilder d = new StringBuilder();
                d.append("{\"field\":\"").append(escapeJson(field)).append("\"");
                if (pv != null) d.append(",\"prev\":\"").append(escapeJson(pv)).append("\"");
                if (cv != null) d.append(",\"curr\":\"").append(escapeJson(cv)).append("\"");
                d.append("}");
                diffs.add(d.toString());
            }
            return diffs.isEmpty() ? null : "[" + String.join(",", diffs) + "]";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 XML 片段中提取语义化的 field→value 映射。
     * 识别条件(atom)和赋值(var-assign)等结构。
     */
    private Map<String, String> extractSemanticFields(String xml) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        Element root = doc.getDocumentElement();

        // 规则自身属性: shadow, enabled, debug, salience, effective-date, expires-date, loop
        Set<String> ruleAttrs = Set.of("shadow", "enabled", "debug", "salience", "effective-date", "expires-date", "loop");
        NamedNodeMap rootAttrs = root.getAttributes();
        if (rootAttrs != null) {
            for (int i = 0; i < rootAttrs.getLength(); i++) {
                Node attr = rootAttrs.item(i);
                String attrName = attr.getNodeName();
                if (ruleAttrs.contains(attrName)) {
                    fields.put(attrName, attr.getNodeValue());
                }
            }
        }

        // 条件: <atom op="..."><left var-label="..."/><value content="..."/></atom>
        NodeList atoms = root.getElementsByTagName("atom");
        for (int i = 0; i < atoms.getLength(); i++) {
            Element atom = (Element) atoms.item(i);
            String op = atom.getAttribute("op");
            String varLabel = "";
            String value = "";
            NodeList children = atom.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j) instanceof Element child) {
                    if ("left".equals(child.getTagName())) {
                        varLabel = child.getAttribute("var-label");
                        if (varLabel.isEmpty()) varLabel = child.getAttribute("var");
                    } else if ("value".equals(child.getTagName())) {
                        value = child.getAttribute("content");
                    }
                }
            }
            String opSymbol = switch (op) {
                case "Equals" -> "==";
                case "GreaterThen" -> ">";
                case "GreaterThenEquals" -> ">=";
                case "LessThen" -> "<";
                case "LessThenEquals" -> "<=";
                case "NotEquals" -> "!=";
                default -> op;
            };
            String fieldKey = varLabel + " " + opSymbol;
            fields.put(fieldKey, value);
        }

        // 赋值: <var-assign var-label="..."><value content="..."/></var-assign>
        NodeList assigns = root.getElementsByTagName("var-assign");
        for (int i = 0; i < assigns.getLength(); i++) {
            Element assign = (Element) assigns.item(i);
            String varLabel = assign.getAttribute("var-label");
            if (varLabel.isEmpty()) varLabel = assign.getAttribute("var");
            NodeList children = assign.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (children.item(j) instanceof Element child && "value".equals(child.getTagName())) {
                    fields.put(varLabel + " =", child.getAttribute("content"));
                }
            }
        }

        // 决策流 node 属性: file, version
        String fileAttr = root.getAttribute("file");
        if (!fileAttr.isEmpty()) {
            fields.put("引用文件", fileAttr);
        }
        String verAttr = root.getAttribute("version");
        if (!verAttr.isEmpty()) {
            fields.put("版本", verAttr);
        }

        // 决策流连接: <connection to="..."/>
        NodeList connections = root.getElementsByTagName("connection");
        for (int i = 0; i < connections.getLength(); i++) {
            Element conn = (Element) connections.item(i);
            String to = conn.getAttribute("to");
            if (!to.isEmpty()) {
                fields.put("连接到", to);
            }
        }

        // 决策表 cell: <cell row="..." col="...">...<value content="..."/>...</cell>
        // value 可能嵌套在 joint/condition 里，用 getElementsByTagName 递归查找
        NodeList cells = root.getElementsByTagName("cell");
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            String row = cell.getAttribute("row");
            String col = cell.getAttribute("col");
            if (row.isEmpty() || col.isEmpty()) continue;
            NodeList values = cell.getElementsByTagName("value");
            if (values.getLength() > 0) {
                Element valueEl = (Element) values.item(0);
                fields.put("R" + row + "C" + col, valueEl.getAttribute("content"));
            }
        }

        return fields;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String elementToString(Element el) throws Exception {
        StringWriter sw = new StringWriter();
        javax.xml.transform.Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.transform(new DOMSource(el), new StreamResult(sw));
        return sw.toString().trim();
    }

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

    /**
     * 锁定版本：遍历 contentMap，把决策流 XML 中引用的 version="LATEST" 替换为该资源的最新版本号。
     * 非决策流文件不处理。修改是 in-place 的。
     */
    public void pinLatestVersions(Map<String, String> contentMap) {
        // 1. 查所有资源的最新版本号：path → versionName
        Map<String, String> latestVersions = new LinkedHashMap<>();
        jdbc.query(
                "SELECT f.path, v.version_name FROM ruleuler_rule_file f " +
                "JOIN ruleuler_rule_file_version v ON f.id = v.file_id " +
                "WHERE v.version = (SELECT MAX(v2.version) FROM ruleuler_rule_file_version v2 WHERE v2.file_id = f.id)",
                rs -> {
                    latestVersions.put(rs.getString("path"), rs.getString("version_name"));
                });

        // 2. 遍历决策流文件，替换 version="LATEST"
        for (Map.Entry<String, String> entry : contentMap.entrySet()) {
            String path = entry.getKey();
            if (!path.toLowerCase().endsWith(".rl.xml")) continue;
            String xml = entry.getValue();
            if (xml == null || !xml.contains("LATEST")) continue;

            String pinned = pinVersionsInFlowXml(xml, latestVersions);
            entry.setValue(pinned);
        }
    }

    /**
     * 把决策流 XML 中 file="dbr:/xxx" version="LATEST" 替换为具体版本号。
     */
    private String pinVersionsInFlowXml(String xml, Map<String, String> latestVersions) {
        // 匹配 file="dbr:/path" version="LATEST"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "file=\"dbr:([^\"]+)\"\\s+version=\"LATEST\"");
        java.util.regex.Matcher matcher = pattern.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String filePath = matcher.group(1);
            String ver = latestVersions.get(filePath);
            if (ver != null) {
                matcher.appendReplacement(sb, "file=\"dbr:" +
                        java.util.regex.Matcher.quoteReplacement(filePath) +
                        "\" version=\"" + ver + "\"");
            }
            // ver 为 null 说明该资源没有版本记录，保持 LATEST
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
