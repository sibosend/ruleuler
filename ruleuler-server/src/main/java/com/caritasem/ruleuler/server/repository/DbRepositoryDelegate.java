package com.caritasem.ruleuler.server.repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bstek.urule.RuleException;
import com.bstek.urule.Utils;
import com.bstek.urule.console.DefaultRepositoryInteceptor;
import com.bstek.urule.console.RepositoryInteceptor;
import com.bstek.urule.console.User;
import com.bstek.urule.console.exception.NoPermissionException;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.NodeLockException;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.model.FileType;
import com.bstek.urule.console.repository.model.LibType;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.ResourceItem;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.bstek.urule.console.repository.model.Type;
import com.bstek.urule.console.repository.model.VersionFile;
import com.bstek.urule.console.repository.permission.PermissionService;
import com.bstek.urule.console.servlet.permission.UserPermission;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.approval.DiffCalculator;
import com.caritasem.ruleuler.server.audit.AuditLogService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component("dbRepositoryDelegate")
public class DbRepositoryDelegate implements RepositoryDelegate, ApplicationContextAware {

    private static final String RES_PACKAGE_FILE = "___res__package__file__";
    private static final String CLIENT_CONFIG_FILE = "___client_config__file__";

    private static final String INIT_RES_PACKAGE_CONTENT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><res-packages></res-packages>";
    private static final String INIT_CLIENT_CONFIG_CONTENT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><client-config></client-config>";

    private JdbcTemplate jdbcTemplate;
    private RepositoryInteceptor repositoryInteceptor;
    private PermissionService permissionService;
    private AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Collection<RepositoryInteceptor> interceptors =
                ctx.getBeansOfType(RepositoryInteceptor.class).values();
        repositoryInteceptor = interceptors.isEmpty()
                ? new DefaultRepositoryInteceptor()
                : interceptors.iterator().next();
        this.permissionService = ctx.getBean(PermissionService.class);
        this.jdbcTemplate = ctx.getBean(JdbcTemplate.class);
        this.auditLogService = ctx.getBean(AuditLogService.class);
    }

    // ==================== 8.2 createProject ====================

    @Override
    public RepositoryFile createProject(String projectName, User user, boolean classify) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        String projectPath = "/" + projectName;
        long now = System.currentTimeMillis();
        String username = user.getUsername();
        String companyId = user.getCompanyId();

        // 检查项目是否已存在
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?",
                Integer.class, projectPath);
        if (count != null && count > 0) {
            throw new RuleException("Project [" + projectName + "] already exist.");
        }

        // 插入项目目录记录
        jdbcTemplate.update(
                "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,1,NULL,?,?,?,?,?)",
                projectName, projectPath, projectName, username, username, companyId, now, now);

        // 插入初始文件
        String resPath = projectPath + "/" + RES_PACKAGE_FILE;
        jdbcTemplate.update(
                "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,0,?,?,?,?,?,?)",
                projectName, resPath, RES_PACKAGE_FILE, INIT_RES_PACKAGE_CONTENT, username, username, companyId, now, now);

        String clientPath = projectPath + "/" + CLIENT_CONFIG_FILE;
        jdbcTemplate.update(
                "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,0,?,?,?,?,?,?)",
                projectName, clientPath, CLIENT_CONFIG_FILE, INIT_CLIENT_CONFIG_CONTENT, username, username, companyId, now, now);

        repositoryInteceptor.createProject(projectName);

        RepositoryFile projectFile = new RepositoryFile();
        projectFile.setType(Type.project);
        projectFile.setName(projectName);
        projectFile.setFullPath(projectPath);
        return projectFile;
    }

    // ==================== 8.3 createFile / createDir ====================

    @Override
    public void createFile(String path, String content, User user) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.createFile(path, content);
        String project = extractProject(path);
        String name = extractName(path);
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,0,?,?,?,?,?,?)",
                project, path, name, content, user.getUsername(), user.getUsername(), user.getCompanyId(), now, now);
        auditLogService.log("CREATE", "FILE", null, path, project, user.getUsername(), Map.of("name", name), null);
    }

    @Override
    public void createDir(String path, User user) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.createDir(path);
        String project = extractProject(path);
        String name = extractName(path);
        long now = System.currentTimeMillis();

        // 检查是否已存在
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?", Integer.class, path);
        if (count != null && count > 0) {
            throw new RuleException("Dir [" + path + "] already exist.");
        }

        jdbcTemplate.update(
                "INSERT INTO ruleuler_rule_file(project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at) VALUES(?,?,?,1,NULL,?,?,?,?,?)",
                project, path, name, user.getUsername(), user.getUsername(), user.getCompanyId(), now, now);
        auditLogService.log("CREATE", "DIR", null, path, project, user.getUsername(), Map.of("name", name), null);
    }

    // ==================== 8.4 saveFile ====================

    @Override
    public void saveFile(String path, String content, boolean newVersion, String versionComment, User user) throws Exception {
        path = Utils.decodeURL(path);

        if (path.indexOf(RES_PACKAGE_FILE) > -1) {
            if (!permissionService.projectPackageHasWritePermission(path)) {
                throw new NoPermissionException();
            }
        }
        if (!permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }

        repositoryInteceptor.saveFile(path, content);

        // 去掉存储前缀 dbr:
        if (path.startsWith("dbr:")) {
            path = path.substring(4);
        }

        // 去掉版本号后缀（如 path:v1）
        int colonPos = path.indexOf(":");
        if (colonPos != -1) {
            path = path.substring(0, colonPos);
        }

        // 确保前导 /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        long now = System.currentTimeMillis();

        // 先查当前 updated_at 用于乐观锁
        Long currentUpdatedAt;
        try {
            currentUpdatedAt = jdbcTemplate.queryForObject(
                    "SELECT updated_at FROM ruleuler_rule_file WHERE path=?", Long.class, path);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        // 乐观锁更新
        int affected = jdbcTemplate.update(
                "UPDATE ruleuler_rule_file SET content=?, update_user=?, updated_at=? WHERE path=? AND updated_at=?",
                content, user.getUsername(), now, path, currentUpdatedAt);
        if (affected == 0) {
            throw new RuleException("文件已被其他用户修改，请刷新后重试");
        }

        // 创建版本：决策组件每次保存自动创建版本（配置文件除外）
        boolean isDecisionComponent = !path.contains(RES_PACKAGE_FILE)
                && !path.contains(CLIENT_CONFIG_FILE)
                && !"未知".equals(DiffCalculator.resolveComponentType(path));
        if (newVersion || isDecisionComponent) {
            Long fileId = jdbcTemplate.queryForObject(
                    "SELECT id FROM ruleuler_rule_file WHERE path=?", Long.class, path);

            Integer maxVersion = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(version),0) FROM ruleuler_rule_file_version WHERE file_id=?",
                    Integer.class, fileId);
            int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
            String versionName = "V" + nextVersion;

            jdbcTemplate.update(
                    "INSERT INTO ruleuler_rule_file_version(file_id,version,version_name,content,comment,create_user,created_at) VALUES(?,?,?,?,?,?,?)",
                    fileId, nextVersion, versionName, content,
                    StringUtils.isNotBlank(versionComment) ? versionComment : null,
                    user.getUsername(), now);
            auditLogService.log("UPDATE", "FILE", fileId, path, extractProject(path), user.getUsername(),
                    Map.of("version", nextVersion, "versionName", versionName), null);
        }
    }

    // ==================== 8.5 readFile ====================

    @Override
    public InputStream readFile(String path) throws Exception {
        return readFile(path, null);
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        if (StringUtils.isNotBlank(version)) {
            repositoryInteceptor.readFile(path + ":" + version);
            return readVersionFile(path, version);
        }

        repositoryInteceptor.readFile(path);

        // 去掉存储前缀 dbr:
        if (path.startsWith("dbr:")) {
            path = path.substring(4);
        }

        // 处理 path 中冒号分隔的版本号
        int colonPos = path.lastIndexOf(":");
        if (colonPos > -1) {
            version = path.substring(colonPos + 1);
            path = path.substring(0, colonPos);
            return readVersionFile(path, version);
        }

        String content;
        try {
            content = jdbcTemplate.queryForObject(
                    "SELECT content FROM ruleuler_rule_file WHERE path=?", String.class, path);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("File [" + path + "] not exist.");
        }
        if (content == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream readVersionFile(String path, String version) throws Exception {
        String content;
        try {
            content = jdbcTemplate.queryForObject(
                    "SELECT v.content FROM ruleuler_rule_file f JOIN ruleuler_rule_file_version v ON f.id=v.file_id WHERE f.path=? AND v.version_name=?",
                    String.class, path, version);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("Version [" + version + "] of file [" + path + "] not exist.");
        }
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== 8.6 deleteFile ====================

    @Override
    public void deleteFile(String path, User user) throws Exception {
        if (!permissionService.fileHasWritePermission(path)) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.deleteFile(path);

        // 查询文件记录
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, is_dir FROM ruleuler_rule_file WHERE path=?", path);
        if (rows.isEmpty()) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        Map<String, Object> row = rows.get(0);
        Long fileId = ((Number) row.get("id")).longValue();
        boolean isDir = toBool(row.get("is_dir"));

        if (isDir) {
            // 递归删除子文件的版本记录
            jdbcTemplate.update(
                    "DELETE FROM ruleuler_rule_file_version WHERE file_id IN (SELECT id FROM ruleuler_rule_file WHERE path LIKE ?)",
                    path + "/%");
            // 删除子文件
            jdbcTemplate.update("DELETE FROM ruleuler_rule_file WHERE path LIKE ?", path + "/%");
        }

        // 删除当前文件的版本记录
        jdbcTemplate.update("DELETE FROM ruleuler_rule_file_version WHERE file_id=?", fileId);
        // 删除当前文件
        jdbcTemplate.update("DELETE FROM ruleuler_rule_file WHERE id=?", fileId);

        auditLogService.log("DELETE", isDir ? "DIR" : "FILE", fileId, path, extractProject(path),
                user.getUsername(), Map.of("isDir", isDir), null);
    }

    // ==================== 8.7 lockPath / unlockPath ====================

    @Override
    public void lockPath(String path, User user) throws Exception {
        path = normalizePath(path);
        int affected = jdbcTemplate.update(
                "UPDATE ruleuler_rule_file SET lock_user=?, lock_time=? WHERE path=? AND lock_user IS NULL",
                user.getUsername(), System.currentTimeMillis(), path);
        if (affected == 0) {
            String lockUser = null;
            try {
                lockUser = jdbcTemplate.queryForObject(
                        "SELECT lock_user FROM ruleuler_rule_file WHERE path=?", String.class, path);
            } catch (EmptyResultDataAccessException e) {
                throw new RuleException("File [" + path + "] not exist.");
            }
            if (lockUser == null) {
                throw new NodeLockException("锁定失败，请重试");
            }
            throw new NodeLockException("【" + path + "】已被" + lockUser + "锁定，您不能进行再次锁定!");
        }
        auditLogService.log("LOCK", "FILE", null, path, extractProject(path), user.getUsername(), null, null);
    }

    @Override
    public void unlockPath(String path, User user) throws Exception {
        path = normalizePath(path);

        String lockUser;
        try {
            lockUser = jdbcTemplate.queryForObject(
                    "SELECT lock_user FROM ruleuler_rule_file WHERE path=?", String.class, path);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        if (lockUser == null) {
            throw new NodeLockException("当前文件未锁定，不需要解锁!");
        }
        if (!lockUser.equals(user.getUsername())) {
            throw new NodeLockException("当前文件由【" + lockUser + "】锁定，您无权解锁!");
        }

        int affected = jdbcTemplate.update(
                "UPDATE ruleuler_rule_file SET lock_user=NULL, lock_time=NULL WHERE path=? AND lock_user=?",
                path, user.getUsername());
        if (affected == 0) {
            throw new NodeLockException("解锁失败，请重试");
        }
        auditLogService.log("UNLOCK", "FILE", null, path, extractProject(path), user.getUsername(), null, null);
    }

    // ==================== 8.8 loadProjects / loadRepository / getDirectories / loadProjectResourcePackages ====================

    @Override
    public List<RepositoryFile> loadProjects(String companyId) throws Exception {
        List<RepositoryFile> projects = new ArrayList<>();
        String sql;
        Object[] params;
        if (StringUtils.isNotEmpty(companyId)) {
            sql = "SELECT DISTINCT project, path FROM ruleuler_rule_file WHERE is_dir=1 AND path NOT LIKE '%/%/%' AND (company_id=? OR company_id IS NULL)";
            params = new Object[]{companyId};
        } else {
            sql = "SELECT DISTINCT project, path FROM ruleuler_rule_file WHERE is_dir=1 AND path NOT LIKE '%/%/%'";
            params = new Object[]{};
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        for (Map<String, Object> row : rows) {
            String projectName = (String) row.get("project");
            RepositoryFile file = new RepositoryFile();
            file.setType(Type.project);
            file.setName(projectName);
            file.setFullPath("/" + projectName);
            projects.add(file);
        }
        return projects;
    }

    @Override
    public Repository loadRepository(String project, User user, boolean classify, FileType[] types, String searchFileName) throws Exception {
        String companyId = user.getCompanyId();
        if (project != null && project.startsWith("/")) {
            project = project.substring(1);
        }

        Repository repo = new Repository();
        List<String> projectNames = new ArrayList<>();
        repo.setProjectNames(projectNames);

        RepositoryFile rootFile = new RepositoryFile();
        rootFile.setFullPath("/");
        rootFile.setName("项目列表");
        rootFile.setType(Type.root);

        List<Map<String, Object>> projectRows;
        if (StringUtils.isNotEmpty(companyId)) {
            projectRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT project FROM ruleuler_rule_file WHERE is_dir=1 AND path NOT LIKE '%/%/%' AND (company_id=? OR company_id IS NULL)",
                    companyId);
        } else {
            projectRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT project FROM ruleuler_rule_file WHERE is_dir=1 AND path NOT LIKE '%/%/%'");
        }

        for (Map<String, Object> pRow : projectRows) {
            String projectName = (String) pRow.get("project");
            if (StringUtils.isNotBlank(project) && !project.equals(projectName)) {
                continue;
            }
            String projectPath = "/" + projectName;
            if (!permissionService.projectHasPermission(projectPath)) {
                continue;
            }
            if (StringUtils.isBlank(project)) {
                projectNames.add(projectName);
            }

            RepositoryFile projectFile = buildDbProjectFile(projectName, types, classify, searchFileName);
            rootFile.addChild(projectFile, false);
        }

        repo.setRootFile(rootFile);
        return repo;
    }

    private RepositoryFile buildDbProjectFile(String projectName, FileType[] types, boolean classify, String searchFileName) {
        RepositoryFile projectFile = new RepositoryFile();
        projectFile.setType(Type.project);
        projectFile.setName(projectName);
        projectFile.setFullPath("/" + projectName);

        String projectPath = "/" + projectName;

        if ((types == null || types.length == 0)
                && permissionService.projectPackageHasReadPermission(projectPath)) {
            RepositoryFile packageFile = new RepositoryFile();
            packageFile.setName("知识包");
            packageFile.setType(Type.resourcePackage);
            packageFile.setFullPath(projectPath);
            projectFile.addChild(packageFile, false);
        }

        RepositoryFile resDir = new RepositoryFile();
        resDir.setFullPath(projectPath);
        resDir.setName("资源");

        FileType[] fileTypes = types;
        if (types == null || types.length == 0) {
            fileTypes = new FileType[]{
                    FileType.VariableLibrary, FileType.ParameterLibrary,
                    FileType.ConstantLibrary, FileType.ActionLibrary,
                    FileType.Ruleset, FileType.RuleFlow,
                    FileType.DecisionTable, FileType.DecisionTree,
                    FileType.ScriptDecisionTable, FileType.UL, FileType.Scorecard,
                    FileType.Rea
            };
        }

        if (classify) {
            resDir.setType(Type.resource);
            buildClassifiedResources(projectName, resDir, fileTypes, searchFileName);
        } else {
            resDir.setType(Type.all);
            resDir.setLibType(LibType.all);
            buildFlatResources(projectName, resDir, fileTypes, searchFileName);
        }

        projectFile.addChild(resDir, false);
        return projectFile;
    }

    private void buildFlatResources(String projectName, RepositoryFile parent, FileType[] types, String searchFileName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT path, name, is_dir, lock_user FROM ruleuler_rule_file WHERE project=? AND path != ? ORDER BY is_dir DESC, path",
                projectName, "/" + projectName);

        Map<String, RepositoryFile> pathMap = new HashMap<>();
        pathMap.put("/" + projectName, parent);

        for (Map<String, Object> row : rows) {
            String path = (String) row.get("path");
            String name = (String) row.get("name");
            boolean isDir = toBool(row.get("is_dir"));
            String lockUser = (String) row.get("lock_user");

            if (name.equals(RES_PACKAGE_FILE) || name.equals(CLIENT_CONFIG_FILE)) {
                continue;
            }

            if (!isDir) {
                if (!matchesFileType(name, types)) continue;
                if (!permissionService.fileHasReadPermission(path)) continue;
                if (StringUtils.isNotBlank(searchFileName)
                        && name.toLowerCase().indexOf(searchFileName.toLowerCase()) == -1) {
                    continue;
                }
            }

            RepositoryFile file = new RepositoryFile();
            file.setFullPath(path);
            file.setName(name);

            if (isDir) {
                file.setType(Type.folder);
                file.setFolderType(Type.all);
            } else {
                file.setType(resolveFileType(name));
            }

            if (lockUser != null) {
                file.setLock(true);
                file.setLockInfo("被" + lockUser + "锁定");
            }

            String parentPath = path.substring(0, path.lastIndexOf("/"));
            RepositoryFile parentFile = pathMap.get(parentPath);
            if (parentFile != null) {
                parentFile.addChild(file, isDir);
            } else {
                parent.addChild(file, isDir);
            }
            pathMap.put(path, file);
        }
    }

    private void buildClassifiedResources(String projectName, RepositoryFile resDir, FileType[] types, String searchFileName) {
        RepositoryFile libDir = new RepositoryFile();
        libDir.setFullPath(resDir.getFullPath());
        libDir.setName("库");
        libDir.setType(Type.lib);
        libDir.setLibType(LibType.res);
        resDir.addChild(libDir, false);

        RepositoryFile rulesLib = createLibFile(resDir, "决策集", LibType.ruleset, Type.ruleLib);
        RepositoryFile dtLib = createLibFile(resDir, "决策表", LibType.decisiontable, Type.decisionTableLib);
        RepositoryFile dtreeLib = createLibFile(resDir, "决策树", LibType.decisiontree, Type.decisionTreeLib);
        RepositoryFile scLib = createLibFile(resDir, "评分卡", LibType.scorecard, Type.scorecardLib);
        RepositoryFile flowLib = createLibFile(resDir, "决策流", LibType.ruleflow, Type.flowLib);

        resDir.addChild(rulesLib, false);
        resDir.addChild(dtLib, false);
        resDir.addChild(dtreeLib, false);
        resDir.addChild(scLib, false);
        resDir.addChild(flowLib, false);

        // folderType 字符串 → 分类 lib 节点
        Map<String, RepositoryFile> typeToLib = new HashMap<>();
        typeToLib.put("lib", libDir);
        typeToLib.put("ruleLib", rulesLib);
        typeToLib.put("decisionTableLib", dtLib);
        typeToLib.put("decisionTreeLib", dtreeLib);
        typeToLib.put("scorecardLib", scLib);
        typeToLib.put("flowLib", flowLib);

        // 查所有记录（含文件夹），按 is_dir DESC, path ASC 保证文件夹先处理、父目录在子目录前
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT path, name, is_dir, folder_type, lock_user FROM ruleuler_rule_file WHERE project=? AND path != ? ORDER BY is_dir DESC, path",
                projectName, "/" + projectName);

        // pathMap: path → RepositoryFile（用于文件夹层级）
        Map<String, RepositoryFile> pathMap = new HashMap<>();
        String projectRoot = "/" + projectName;

        // 第一遍：处理文件夹
        for (Map<String, Object> row : rows) {
            if (!toBool(row.get("is_dir"))) continue;
            String path = (String) row.get("path");
            String name = (String) row.get("name");
            String folderTypeStr = (String) row.get("folder_type");
            String lockUser = (String) row.get("lock_user");

            RepositoryFile folder = new RepositoryFile();
            folder.setFullPath(path);
            folder.setName(name);
            folder.setType(Type.folder);
            if (lockUser != null) {
                folder.setLock(true);
                folder.setLockInfo("被" + lockUser + "锁定");
            }

            // 确定父节点：先看是否在已有文件夹下，否则按 folderType 挂到分类 lib
            String parentPath = path.substring(0, path.lastIndexOf("/"));
            RepositoryFile parentFile = pathMap.get(parentPath);
            if (parentFile != null) {
                // 子文件夹继承父文件夹的 folderType
                folder.setFolderType(parentFile.getFolderType());
                parentFile.addChild(folder, true);
            } else {
                // 顶层文件夹，按 folder_type 挂到对应分类 lib
                RepositoryFile lib = folderTypeStr != null ? typeToLib.get(folderTypeStr) : null;
                if (lib != null) {
                    Type ft = lib.getType();
                    folder.setFolderType(ft);
                    lib.addChild(folder, true);
                }
                // folder_type 为空或无法匹配的文件夹不显示（历史数据兼容）
            }
            pathMap.put(path, folder);
        }

        // 第二遍：处理文件
        for (Map<String, Object> row : rows) {
            if (toBool(row.get("is_dir"))) continue;
            String path = (String) row.get("path");
            String name = (String) row.get("name");
            String lockUser = (String) row.get("lock_user");

            if (name.equals(RES_PACKAGE_FILE) || name.equals(CLIENT_CONFIG_FILE)) continue;
            if (!permissionService.fileHasReadPermission(path)) continue;
            if (StringUtils.isNotBlank(searchFileName)
                    && name.toLowerCase().indexOf(searchFileName.toLowerCase()) == -1) continue;

            RepositoryFile file = new RepositoryFile();
            file.setFullPath(path);
            file.setName(name);
            file.setType(resolveFileType(name));
            if (lockUser != null) {
                file.setLock(true);
                file.setLockInfo("被" + lockUser + "锁定");
            }

            // 如果文件在子文件夹下，检查文件类型是否匹配父目录 folderType
            String parentPath = path.substring(0, path.lastIndexOf("/"));
            RepositoryFile parentFile = pathMap.get(parentPath);
            RepositoryFile targetLib = resolveTargetLib(name, libDir, rulesLib, dtLib, dtreeLib, scLib, flowLib);
            if (parentFile != null && (targetLib == null || isSameLib(parentFile, targetLib))) {
                parentFile.addChild(file, false);
            } else if (targetLib != null) {
                targetLib.addChild(file, false);
            } else if (parentFile != null) {
                parentFile.addChild(file, false);
            } else {
                // 直接在项目根下的文件，按类型分配到分类 lib
                String nameLower = name.toLowerCase();
                if (nameLower.endsWith(FileType.VariableLibrary.toString())
                        || nameLower.endsWith(FileType.ParameterLibrary.toString())
                        || nameLower.endsWith(FileType.ConstantLibrary.toString())
                        || nameLower.endsWith(FileType.ActionLibrary.toString())) {
                    libDir.addChild(file, false);
                } else if (nameLower.endsWith(FileType.Ruleset.toString())
                        || nameLower.endsWith(FileType.UL.toString())
                        || nameLower.endsWith(FileType.Rea.toString())) {
                    rulesLib.addChild(file, false);
                } else if (nameLower.endsWith(FileType.DecisionTable.toString())
                        || nameLower.endsWith(FileType.ScriptDecisionTable.toString())) {
                    dtLib.addChild(file, false);
                } else if (nameLower.endsWith(FileType.DecisionTree.toString())) {
                    dtreeLib.addChild(file, false);
                } else if (nameLower.endsWith(FileType.Scorecard.toString())) {
                    scLib.addChild(file, false);
                } else if (nameLower.endsWith(FileType.RuleFlow.toString())) {
                    flowLib.addChild(file, false);
                }
            }
        }
    }


    private RepositoryFile createLibFile(RepositoryFile parent, String name, LibType libType, Type type) {
        RepositoryFile lib = new RepositoryFile();
        lib.setFullPath(parent.getFullPath());
        lib.setName(name);
        lib.setLibType(libType);
        lib.setType(type);
        return lib;
    }

    @Override
    public List<RepositoryFile> getDirectories(String project) throws Exception {
        List<RepositoryFile> fileList = new ArrayList<>();
        String projectPath = "/" + project;

        RepositoryFile root = new RepositoryFile();
        root.setName("根目录");
        root.setFullPath(projectPath);
        fileList.add(root);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT path, name FROM ruleuler_rule_file WHERE project=? AND is_dir=1 AND path != ? ORDER BY path",
                project, projectPath);

        for (Map<String, Object> row : rows) {
            String path = (String) row.get("path");
            RepositoryFile file = new RepositoryFile();
            file.setName(path.substring(projectPath.length()));
            file.setFullPath(path);
            fileList.add(file);
        }
        return fileList;
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        String filePath = "/" + processPath(project) + "/" + RES_PACKAGE_FILE;
        String content;
        try {
            content = jdbcTemplate.queryForObject(
                    "SELECT content FROM ruleuler_rule_file WHERE path=?", String.class, filePath);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("Resource package file not found for project: " + project);
        }
        if (content == null) {
            return new ArrayList<>();
        }

        SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        List<ResourcePackage> packages = new ArrayList<>();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) continue;
            Element element = (Element) obj;
            if (!element.getName().equals("res-package")) continue;

            ResourcePackage p = new ResourcePackage();
            String dateStr = element.attributeValue("create_date");
            if (dateStr != null) {
                p.setCreateDate(sd.parse(dateStr));
            }
            p.setId(element.attributeValue("id"));
            p.setName(element.attributeValue("name"));
            p.setProject(project);
            List<ResourceItem> items = new ArrayList<>();
            for (Object o : element.elements()) {
                if (!(o instanceof Element)) continue;
                Element ele = (Element) o;
                if (!ele.getName().equals("res-package-item")) continue;
                ResourceItem item = new ResourceItem();
                item.setName(ele.attributeValue("name"));
                item.setPackageId(p.getId());
                item.setPath(ele.attributeValue("path"));
                item.setVersion(ele.attributeValue("version"));
                items.add(item);
            }
            p.setResourceItems(items);
            packages.add(p);
        }
        return packages;
    }

    // ==================== 8.9 fileRename / fileExistCheck / getReferenceFiles / getVersionFiles ====================

    @Override
    public void fileRename(String path, String newPath) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        repositoryInteceptor.renameFile(path, newPath);

        String newName = extractName(newPath);
        String newProject = extractProject(newPath);

        int affected = jdbcTemplate.update(
                "UPDATE ruleuler_rule_file SET path=?, name=?, project=? WHERE path=?",
                newPath, newName, newProject, path);
        if (affected == 0) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        jdbcTemplate.update(
                "UPDATE ruleuler_rule_file SET path=CONCAT(?, SUBSTRING(path, ?)), project=? WHERE path LIKE ?",
                newPath, path.length() + 1, newProject, path + "/%");

        auditLogService.log("RENAME", "FILE", null, newPath, newProject, getOperator(),
                Map.of("oldPath", path, "newPath", newPath), null);
    }

    @Override
    public boolean fileExistCheck(String filePath) throws Exception {
        if (filePath.contains(" ") || filePath.equals("")) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?", Integer.class, filePath);
        return count != null && count > 0;
    }

    @Override
    public List<String> getReferenceFiles(String path, String searchText) throws Exception {
        String project = extractProject(path);
        List<String> referenceFiles = new ArrayList<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT path, content FROM ruleuler_rule_file WHERE project=? AND is_dir=0 AND content IS NOT NULL",
                project);

        for (Map<String, Object> row : rows) {
            String filePath = (String) row.get("path");
            String content = (String) row.get("content");
            if (content.contains(path) && content.contains(searchText)) {
                referenceFiles.add(filePath);
            }
        }
        return referenceFiles;
    }

    @Override
    public List<VersionFile> getVersionFiles(String path) throws Exception {
        int colonPos = path.lastIndexOf(":");
        if (colonPos > -1) {
            path = path.substring(0, colonPos);
        }

        Long fileId;
        try {
            fileId = jdbcTemplate.queryForObject(
                    "SELECT id FROM ruleuler_rule_file WHERE path=?", Long.class, path);
        } catch (EmptyResultDataAccessException e) {
            throw new RuleException("File [" + path + "] not exist.");
        }

        List<VersionFile> files = new ArrayList<>();
        String finalPath = path;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version_name, create_user, comment, created_at FROM ruleuler_rule_file_version WHERE file_id=? ORDER BY version",
                fileId);

        for (Map<String, Object> row : rows) {
            VersionFile vf = new VersionFile();
            vf.setName((String) row.get("version_name"));
            vf.setPath(finalPath);
            vf.setCreateUser((String) row.get("create_user"));
            vf.setComment((String) row.get("comment"));
            long createdAt = ((Number) row.get("created_at")).longValue();
            vf.setCreateDate(new Date(createdAt));
            files.add(vf);
        }
        return files;
    }

    // ==================== 8.10 exportXml / importXml ====================

    @Override
    public void exportXml(String projectPath, OutputStream outputStream) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        String project = extractProject(projectPath);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT project, path, name, is_dir, content, create_user, update_user, company_id, created_at, updated_at FROM ruleuler_rule_file WHERE project=?",
                project);

        byte[] json = objectMapper.writeValueAsBytes(rows);
        outputStream.write(json);
        outputStream.flush();
    }

    @Override
    public void importXml(InputStream inputStream, boolean overwrite) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }

        byte[] data = IOUtils.toByteArray(inputStream);
        List<Map<String, Object>> rows = objectMapper.readValue(data,
                new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> row : rows) {
            String path = (String) row.get("path");
            String project = (String) row.get("project");
            String name = (String) row.get("name");
            boolean isDirBool = toBool(row.get("is_dir"));
            int isDir = isDirBool ? 1 : 0;
            String content = (String) row.get("content");
            String createUser = (String) row.get("create_user");
            String updateUser = (String) row.get("update_user");
            String companyId = (String) row.get("company_id");
            long createdAt = row.get("created_at") instanceof Number ? ((Number) row.get("created_at")).longValue() : System.currentTimeMillis();
            long updatedAt = row.get("updated_at") instanceof Number ? ((Number) row.get("updated_at")).longValue() : System.currentTimeMillis();

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
                    project, path, name, isDir, content, createUser, updateUser, companyId, createdAt, updatedAt);
        }
    }

    // ==================== 8.11 loadClientConfigs / loadResourceSecurityConfigs ====================

    @Override
    public List<ClientConfig> loadClientConfigs(String project) throws Exception {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        String filePath = "/" + processPath(project) + "/" + CLIENT_CONFIG_FILE;
        String content;
        try {
            content = jdbcTemplate.queryForObject(
                    "SELECT content FROM ruleuler_rule_file WHERE path=?", String.class, filePath);
        } catch (EmptyResultDataAccessException e) {
            return new ArrayList<>();
        }
        if (content == null) {
            return new ArrayList<>();
        }

        List<ClientConfig> clients = new ArrayList<>();
        Document document = DocumentHelper.parseText(content);
        Element rootElement = document.getRootElement();
        for (Object obj : rootElement.elements()) {
            if (!(obj instanceof Element)) continue;
            Element element = (Element) obj;
            if (!element.getName().equals("item")) continue;
            ClientConfig client = new ClientConfig();
            client.setName(element.attributeValue("name"));
            client.setClient(element.attributeValue("client"));
            client.setProject(project);
            clients.add(client);
        }
        return clients;
    }

    @Override
    public List<UserPermission> loadResourceSecurityConfigs(String companyId) throws Exception {
        throw new UnsupportedOperationException("DB delegate does not support loadResourceSecurityConfigs, route to JCR delegate instead.");
    }

    // ==================== 工具方法 ====================

    private String extractProject(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        int pos = path.indexOf("/");
        if (pos == -1) return path;
        return path.substring(0, pos);
    }

    private String extractName(String path) {
        int pos = path.lastIndexOf("/");
        if (pos == -1) return path;
        return path.substring(pos + 1);
    }

    private String processPath(String path) {
        if (path.startsWith("/")) return path.substring(1);
        return path;
    }

    private String normalizePath(String path) {
        int pos = path.indexOf(":");
        if (pos != -1) {
            path = path.substring(0, pos);
        }
        return path;
    }

    private boolean matchesFileType(String name, FileType[] types) {
        String nameLower = name.toLowerCase();
        for (FileType type : types) {
            if (nameLower.endsWith(type.toString())) return true;
        }
        return false;
    }

    /** MySQL TINYINT(1) 可能返回 Boolean 或 Number，统一处理 */
    private static boolean toBool(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() == 1;
        throw new IllegalArgumentException("Unexpected is_dir type: " + (val == null ? "null" : val.getClass().getName()));
    }

    private static String getOperator() {
        AuthContext.UserInfo user = AuthContext.get();
        return user != null ? user.getUsername() : "system";
    }

    private RepositoryFile resolveTargetLib(String name, RepositoryFile libDir, RepositoryFile rulesLib,
                                              RepositoryFile dtLib, RepositoryFile dtreeLib, RepositoryFile scLib, RepositoryFile flowLib) {
        String nl = name.toLowerCase();
        if (nl.endsWith(FileType.VariableLibrary.toString()) || nl.endsWith(FileType.ParameterLibrary.toString())
                || nl.endsWith(FileType.ConstantLibrary.toString()) || nl.endsWith(FileType.ActionLibrary.toString())) return libDir;
        if (nl.endsWith(FileType.Ruleset.toString()) || nl.endsWith(FileType.UL.toString()) || nl.endsWith(FileType.Rea.toString())) return rulesLib;
        if (nl.endsWith(FileType.DecisionTable.toString()) || nl.endsWith(FileType.ScriptDecisionTable.toString())) return dtLib;
        if (nl.endsWith(FileType.DecisionTree.toString())) return dtreeLib;
        if (nl.endsWith(FileType.Scorecard.toString())) return scLib;
        if (nl.endsWith(FileType.RuleFlow.toString())) return flowLib;
        return null;
    }

    private boolean isSameLib(RepositoryFile folder, RepositoryFile targetLib) {
        // 向上找 folder 或其祖先是否挂在 targetLib 下
        Type ft = folder.getFolderType();
        if (ft == null) return false;
        return ft.equals(targetLib.getType());
    }

    private Type resolveFileType(String name) {
        String nameLower = name.toLowerCase();
        if (nameLower.endsWith(FileType.ActionLibrary.toString())) return Type.action;
        if (nameLower.endsWith(FileType.VariableLibrary.toString())) return Type.variable;
        if (nameLower.endsWith(FileType.ConstantLibrary.toString())) return Type.constant;
        if (nameLower.endsWith(FileType.ParameterLibrary.toString())) return Type.parameter;
        if (nameLower.endsWith(FileType.Ruleset.toString())) return Type.rule;
        if (nameLower.endsWith(FileType.UL.toString())) return Type.ul;
        if (nameLower.endsWith(FileType.ScriptDecisionTable.toString())) return Type.scriptDecisionTable;
        if (nameLower.endsWith(FileType.DecisionTable.toString())) return Type.decisionTable;
        if (nameLower.endsWith(FileType.DecisionTree.toString())) return Type.decisionTree;
        if (nameLower.endsWith(FileType.RuleFlow.toString())) return Type.flow;
        if (nameLower.endsWith(FileType.Scorecard.toString())) return Type.scorecard;
        if (nameLower.endsWith(FileType.Rea.toString())) return Type.rea;
        return Type.all;
    }
}
