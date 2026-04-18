/*******************************************************************************
 * Copyright 2017 Bstek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.urule.server.repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.caritasem.ruleuler.server.repository.DbRepositoryDelegate;
import com.caritasem.ruleuler.server.repository.ProjectStorageService;
import com.caritasem.ruleuler.server.repository.RepositoryDelegate;
import com.caritasem.ruleuler.server.repository.StorageContext;
import com.caritasem.ruleuler.server.repository.StorageType;

import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.FileType;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.bstek.urule.console.repository.model.VersionFile;
import com.bstek.urule.console.servlet.permission.UserPermission;

/**
 * 路由器模式：根据项目存储类型委托给 JcrRepositoryDelegate 或 DbRepositoryDelegate。
 * 不再继承 BaseRepositoryService，零 JCR 依赖。
 */
public class RepositoryServiceRouter implements RepositoryService, ApplicationContextAware {

    private JcrRepositoryDelegate jcrDelegate;
    private DbRepositoryDelegate dbDelegate;
    private ProjectStorageService projectStorageService;
    private JdbcTemplate jdbcTemplate;

    // ---- 工具方法 ----

    /**
     * 统一清理路径：decode + 去掉存储前缀 dbr:/jcr:。
     * 所有传给 delegate 的 path 都必须经过此方法。
     */
    static String cleanPath(String path) {
        path = com.bstek.urule.Utils.decodeURL(path);
        if (path.startsWith("dbr:")) {
            path = path.substring(4);
        } else if (path.startsWith("jcr:")) {
            path = path.substring(4);
        }
        return path;
    }

    /**
     * 从路径中提取项目名。
     * /project/dir/file.rl → project
     * project/dir/file.rl → project
     */
    static String extractProject(String path) {
        path = cleanPath(path);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        int pos = path.indexOf("/");
        if (pos == -1) {
            return path;
        }
        return path.substring(0, pos);
    }

    /**
     * 根据路径中的项目名查询存储类型，返回对应 delegate。
     */
    private RepositoryDelegate resolveDelegate(String path) {
        String project = extractProject(path);
        return resolveDelegateByProject(project);
    }

    /**
     * 根据项目名查询存储类型，返回对应 delegate。
     */
    private RepositoryDelegate resolveDelegateByProject(String project) {
        if (project != null && project.startsWith("/")) {
            project = project.substring(1);
        }
        StorageType type = projectStorageService.getStorageTypeOrNull(project);
        // 未注册的项目默认走 JCR（向后兼容老项目）
        return type == StorageType.DB ? dbDelegate : jcrDelegate;
    }

    // ---- 特殊路由方法 ----

    @Override
    public RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception {
        StorageType type = StorageContext.get();
        try {
            RepositoryDelegate delegate = type == StorageType.JCR ? jcrDelegate : dbDelegate;
            RepositoryFile result = delegate.createProject(projectName, user, classify);
            projectStorageService.register(projectName, type);
            return result;
        } finally {
            StorageContext.clear();
        }
    }

    @Override
    public List<RepositoryFile> loadProjects(String companyId) throws Exception {
        List<RepositoryFile> result = new ArrayList<>();
        for (RepositoryFile f : jcrDelegate.loadProjects(companyId)) {
            f.setStorageType("jcr");
            result.add(f);
        }
        for (RepositoryFile f : dbDelegate.loadProjects(companyId)) {
            f.setStorageType("db");
            result.add(f);
        }
        return result;
    }

    @Override
    public List<UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception {
        return jcrDelegate.loadResourceSecurityConfigs(companyId);
    }

    @Override
    public boolean fileExistCheck(String filePath) throws Exception {
        filePath = cleanPath(filePath);
        return resolveDelegate(filePath).fileExistCheck(filePath);
    }

    // ---- 路径路由方法（从 path 提取 project） ----

    @Override
    public void createDir(String path, User user) throws Exception {
        path = cleanPath(path);
        resolveDelegate(path).createDir(path, user);
    }

    @Override
    public void createFile(String path, String content, User user) throws Exception {
        path = cleanPath(path);
        resolveDelegate(path).createFile(path, content, user);
    }

    @Override
    public void saveFile(String path, String content, boolean newVersion, String versionComment, User user)
            throws Exception {
        path = cleanPath(path);
        resolveDelegate(path).saveFile(path, content, newVersion, versionComment, user);
    }

    @Override
    public void deleteFile(String path, User user) throws Exception {
        path = cleanPath(path);
        String project = extractProject(path);
        String normalized = path.startsWith("/") ? path : "/" + path;
        boolean isProjectDelete = normalized.equals("/" + project);
        if (isProjectDelete) {
            RepositoryDelegate delegate = resolveDelegate(path);
            if (!delegate.fileExistCheck(normalized)) {
                projectStorageService.unregister(project);
                return;
            }
            delegate.deleteFile(path, user);
            projectStorageService.unregister(project);
            return;
        }
        resolveDelegate(path).deleteFile(path, user);
    }

    @Override
    public void lockPath(String path, User user) throws Exception {
        path = cleanPath(path);
        resolveDelegate(path).lockPath(path, user);
    }

    @Override
    public void unlockPath(String path, User user) throws Exception {
        path = cleanPath(path);
        resolveDelegate(path).unlockPath(path, user);
    }

    @Override
    public void fileRename(String path, String newPath) throws Exception {
        path = cleanPath(path);
        newPath = cleanPath(newPath);
        resolveDelegate(path).fileRename(path, newPath);
    }

    @Override
    public List<String> getReferenceFiles(String path, String searchText) throws Exception {
        path = cleanPath(path);
        return resolveDelegate(path).getReferenceFiles(path, searchText);
    }

    @Override
    public InputStream readFile(String path) throws Exception {
        path = cleanPath(path);
        return resolveDelegate(path).readFile(path);
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        path = cleanPath(path);
        return resolveDelegate(path).readFile(path, version);
    }

    @Override
    public List<VersionFile> getVersionFiles(String path) throws Exception {
        path = cleanPath(path);
        return resolveDelegate(path).getVersionFiles(path);
    }

    @Override
    public void exportXml(String projectPath, OutputStream outputStream) throws Exception {
        projectPath = cleanPath(projectPath);
        resolveDelegate(projectPath).exportXml(projectPath, outputStream);
    }

    @Override
    public void importXml(InputStream inputStream, boolean overwrite) throws Exception {
        byte[] data = IOUtils.toByteArray(inputStream);
        String head = new String(data, 0, Math.min(data.length, 1), StandardCharsets.UTF_8).trim();

        if (head.equals("[")) {
            // DBR JSON 格式
            dbDelegate.importXml(new ByteArrayInputStream(data), overwrite);
        } else if (head.equals("<")) {
            // JCR system view XML → 转 DBR
            convertJcrXmlToDb(data, overwrite);
        } else {
            throw new IllegalArgumentException("无法识别的导入文件格式");
        }
    }

    private void convertJcrXmlToDb(byte[] xmlData, boolean overwrite) throws Exception {
        // 去掉命名空间前缀，避免 dom4j 命名空间解析问题
        String xml = new String(xmlData, StandardCharsets.UTF_8);
        xml = xml.replaceAll("<(/?)sv:", "<$1").replaceAll("<(/?)jcr:", "<$1")
                .replaceAll("<(/?)nt:", "<$1").replaceAll("<(/?)mix:", "<$1").replaceAll("<(/?)rep:", "<$1");

        Document doc = DocumentHelper.parseText(xml);
        Element root = doc.getRootElement();
        String rootNodeName = root.attributeValue("name");
        String projectName = rootNodeName;

        List<Map<String, Object>> records = new ArrayList<>();
        parseJcrNode(root, "/" + projectName, projectName, records);

        // 写入数据库
        for (Map<String, Object> rec : records) {
            String path = (String) rec.get("path");
            if (overwrite) {
                jdbcTemplate.update("DELETE FROM ruleuler_rule_file WHERE path=?", path);
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?", Integer.class, path);
            if (count != null && count > 0) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    rec.get("project"), rec.get("path"), rec.get("name"), rec.get("is_dir"),
                    rec.get("content"), rec.get("create_user"), rec.get("update_user"),
                    rec.get("company_id"), rec.get("created_at"), rec.get("updated_at"));
        }

        // 为目录推断 folder_type：根据子文件扩展名判定分类
        inferFolderTypes(projectName);

        // 注册为 DB 存储
        StorageType existing = projectStorageService.getStorageTypeOrNull(projectName);
        if (existing == null) {
            projectStorageService.register(projectName, StorageType.DB);
        }
    }

    private void inferFolderTypes(String project) {
        String projectPath = "/" + project;

        // 按深度倒序处理（深→浅），跳过项目根目录，每个目录只看直接子文件
        List<Map<String, Object>> dirs = jdbcTemplate.queryForList(
                "SELECT path FROM ruleuler_rule_file WHERE project=? AND is_dir=1 AND path != ? AND (folder_type IS NULL OR folder_type='') ORDER BY LENGTH(path) DESC",
                project, projectPath);

        for (Map<String, Object> row : dirs) {
            String dirPath = (String) row.get("path");
            // 只看直接子文件（排除更深层的文件）
            List<String> childNames = jdbcTemplate.queryForList(
                    "SELECT name FROM ruleuler_rule_file WHERE project=? AND path LIKE ? AND is_dir=0 AND path NOT LIKE ?",
                    String.class, project, dirPath + "/%", dirPath + "/%/%");
            String folderType = resolveFolderType(childNames);
            if (folderType != null) {
                jdbcTemplate.update("UPDATE ruleuler_rule_file SET folder_type=? WHERE path=?", folderType, dirPath);
            }
        }

        // 剩余无 folder_type 的目录，从已有 folder_type 的祖先继承（浅→深）
        List<Map<String, Object>> remaining = jdbcTemplate.queryForList(
                "SELECT path FROM ruleuler_rule_file WHERE project=? AND is_dir=1 AND path != ? AND (folder_type IS NULL OR folder_type='') ORDER BY path",
                project, projectPath);
        for (Map<String, Object> row : remaining) {
            String dirPath = (String) row.get("path");
            String parent = dirPath;
            while (true) {
                int idx = parent.lastIndexOf("/");
                if (idx <= 0) break;
                parent = parent.substring(0, idx);
                try {
                    String pt = jdbcTemplate.queryForObject(
                            "SELECT folder_type FROM ruleuler_rule_file WHERE path=? AND folder_type IS NOT NULL AND folder_type != ''",
                            String.class, parent);
                    if (pt != null) {
                        jdbcTemplate.update("UPDATE ruleuler_rule_file SET folder_type=? WHERE path=?", pt, dirPath);
                        break;
                    }
                } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
            }
        }
    }

    private String resolveFolderType(List<String> childNames) {
        int lib = 0, rule = 0, dt = 0, dtree = 0, sc = 0, flow = 0;
        for (String name : childNames) {
            String nl = name.toLowerCase();
            // 只统计直接子文件（跳过子目录下的文件）
            if (nl.endsWith(".vl.xml") || nl.endsWith(".cl.xml") || nl.endsWith(".pl.xml") || nl.endsWith(".al.xml")) lib++;
            else if (nl.endsWith(".rs.xml") || nl.endsWith(".ul") || nl.endsWith(".rea.xml")) rule++;
            else if (nl.endsWith(".dt.xml") || nl.endsWith(".dts.xml")) dt++;
            else if (nl.endsWith(".dtree.xml")) dtree++;
            else if (nl.endsWith(".sc")) sc++;
            else if (nl.endsWith(".rl.xml")) flow++;
        }
        // 混合类型取最多的，单一类型直接返回
        if (dtree > 0 && dtree >= dt && dtree >= rule && dtree >= flow && dtree >= sc && dtree >= lib) return "decisionTreeLib";
        if (dt > 0 && dt >= dtree && dt >= rule && dt >= flow && dt >= sc && dt >= lib) return "decisionTableLib";
        if (flow > 0 && flow >= dtree && flow >= dt && flow >= rule && flow >= sc && flow >= lib) return "flowLib";
        if (sc > 0 && sc >= dtree && sc >= dt && sc >= rule && flow >= lib) return "scorecardLib";
        if (rule > 0 && rule >= dtree && rule >= dt && rule >= flow && rule >= sc && rule >= lib) return "ruleLib";
        if (lib > 0) return "lib";
        return null;
    }

    private void parseJcrNode(Element node, String path, String project, List<Map<String, Object>> records) {
        // 提取属性
        Map<String, String> props = new HashMap<>();
        for (Element prop : (List<Element>) node.elements("property")) {
            String propName = prop.attributeValue("name");
            if (propName == null || propName.startsWith("jcr:")) continue;
            String propType = prop.attributeValue("type");
            Element valueEl = (Element) prop.element("value");
            if (valueEl != null) {
                String val = valueEl.getTextTrim();
                // JCR system view 中 Binary 类型值是 Base64 编码
                if ("Binary".equals(propType) && val != null && !val.isEmpty()) {
                    val = new String(java.util.Base64.getDecoder().decode(val), StandardCharsets.UTF_8);
                    // 内容中的资源引用路径从 jcr: 转 dbr:
                    val = val.replace("jcr:/", "dbr:/");
                }
                props.put(propName, val);
            }
        }

        String name = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
        boolean isDir = "true".equals(props.get("_dir"));
        boolean isFile = "true".equals(props.get("_file"));

        // 有 _file 属性的节点才作为记录（跳过纯 JCR 中间节点）
        if (isFile) {
            boolean hasChildNodes = !node.elements("node").isEmpty();
            boolean dirFlag = isDir || (hasChildNodes && !props.containsKey("_data"));

            Map<String, Object> rec = new HashMap<>();
            rec.put("project", project);
            rec.put("path", path);
            rec.put("name", name);
            rec.put("is_dir", dirFlag ? 1 : 0);
            rec.put("content", props.get("_data"));
            rec.put("create_user", props.getOrDefault("_create_user", "import"));
            rec.put("update_user", props.getOrDefault("_create_user", "import"));
            rec.put("company_id", props.get("_company_id"));
            long ts = System.currentTimeMillis();
            rec.put("created_at", ts);
            rec.put("updated_at", ts);
            records.add(rec);
        }

        // 递归子节点
        for (Element child : (List<Element>) node.elements("node")) {
            String childName = child.attributeValue("name");
            if (childName == null) continue;
            parseJcrNode(child, path + "/" + childName, project, records);
        }
    }

    // ---- 项目名路由方法（直接用 project 名查询） ----

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types,
            String searchFileName) throws Exception {
        if (project == null || project.isEmpty()) {
            // 加载所有项目：合并 JCR 和 DB 的结果
            return mergeRepositories(user, classify, types, searchFileName);
        }
        StorageType st = projectStorageService.getStorageTypeOrNull(project);
        String stLabel = (st == StorageType.DB) ? "db" : "jcr";
        Repository repo = resolveDelegateByProject(project).loadRepository(project, user, classify, types, searchFileName);
        if (repo.getRootFile() != null && repo.getRootFile().getChildren() != null) {
            for (RepositoryFile child : repo.getRootFile().getChildren()) {
                child.setStorageType(stLabel);
            }
        }
        return repo;
    }

    private Repository mergeRepositories(User user, boolean classify, FileType[] types,
            String searchFileName) throws Exception {
        Repository jcrRepo = jcrDelegate.loadRepository(null, user, classify, types, searchFileName);
        Repository dbRepo = dbDelegate.loadRepository(null, user, classify, types, searchFileName);

        // 给项目节点打上存储类型标记
        if (jcrRepo.getRootFile() != null && jcrRepo.getRootFile().getChildren() != null) {
            for (RepositoryFile child : jcrRepo.getRootFile().getChildren()) {
                child.setStorageType("jcr");
            }
        }

        // 以 JCR 结果为基础，合并 DB 项目
        if (dbRepo.getRootFile() != null && dbRepo.getRootFile().getChildren() != null) {
            for (RepositoryFile child : dbRepo.getRootFile().getChildren()) {
                child.setStorageType("db");
                jcrRepo.getRootFile().addChild(child, false);
            }
        }
        if (dbRepo.getProjectNames() != null) {
            jcrRepo.getProjectNames().addAll(dbRepo.getProjectNames());
        }
        return jcrRepo;
    }

    @Override
    public List<RepositoryFile> getDirectories(String project) throws Exception {
        return resolveDelegateByProject(project).getDirectories(project);
    }

    @Override
    public List<ClientConfig> loadClientConfigs(String project) throws Exception {
        return resolveDelegateByProject(project).loadClientConfigs(project);
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        return resolveDelegateByProject(project).loadProjectResourcePackages(project);
    }

    // ---- Spring 装配 ----

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.jcrDelegate = ctx.getBean(JcrRepositoryDelegate.class);
        this.dbDelegate = ctx.getBean(DbRepositoryDelegate.class);
        this.projectStorageService = ctx.getBean(ProjectStorageService.class);
        this.jdbcTemplate = ctx.getBean(JdbcTemplate.class);
    }
}
