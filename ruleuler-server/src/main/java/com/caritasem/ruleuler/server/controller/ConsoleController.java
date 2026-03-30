package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.VersionFile;
import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.StringWriter;

import java.util.List;
import java.util.Map;

/**
 * 规则编辑器资源树 REST API。
 * 替代旧的 /urule/frame POST + action 方式，走 JWT 认证。
 */
@RestController
@RequestMapping("/api/console")
public class ConsoleController {

    private final RepositoryService repositoryService;
    private final JdbcTemplate jdbcTemplate;

    public ConsoleController(@Qualifier("urule.repositoryService") RepositoryService repositoryService,
                             JdbcTemplate jdbcTemplate) {
        this.repositoryService = repositoryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private User currentUser() {
        AuthContext.UserInfo info = AuthContext.get();
        DefaultUser u = new DefaultUser();
        u.setUsername(info.getUsername());
        u.setAdmin(info.getRoles() != null && info.getRoles().contains("admin"));
        return u;
    }

    /** 加载项目文件树 */
    @GetMapping("/tree/{project}")
    public ApiResult loadTree(@PathVariable String project) throws Exception {
        Repository repo = repositoryService.loadRepository(project, currentUser(), true, null, null);
        clearParentRefs(repo.getRootFile());
        return ApiResult.ok(Map.of("repo", repo, "classify", true));
    }

    /** 递归清除 parentFile 避免 Jackson 2.x 序列化循环引用 */
    private void clearParentRefs(RepositoryFile file) {
        if (file == null) return;
        file.setParentFile(null);
        List<RepositoryFile> children = file.getChildren();
        if (children != null) {
            for (RepositoryFile child : children) {
                clearParentRefs(child);
            }
        }
    }

    /** 创建文件 */
    @PostMapping("/file")
    public ApiResult createFile(@RequestBody Map<String, String> body) throws Exception {
        repositoryService.createFile(body.get("path"), body.get("content"), currentUser());
        return ApiResult.ok(null);
    }

    /** 创建文件夹 */
    @PostMapping("/folder")
    public ApiResult createFolder(@RequestBody Map<String, String> body) throws Exception {
        String path = body.get("path");
        String folderType = body.get("folderType");
        repositoryService.createDir(path, currentUser());
        if (folderType != null && !folderType.isEmpty()) {
            jdbcTemplate.update("UPDATE ruleuler_rule_file SET folder_type=? WHERE path=?", folderType, path);
        }
        return ApiResult.ok(null);
    }

    /** 删除文件或文件夹 */
    @DeleteMapping("/file")
    public ApiResult deleteFile(@RequestParam String path) throws Exception {
        repositoryService.deleteFile(path, currentUser());
        return ApiResult.ok(null);
    }

    /** 重命名 */
    @PostMapping("/rename")
    public ApiResult fileRename(@RequestBody Map<String, String> body) throws Exception {
        repositoryService.fileRename(body.get("path"), body.get("newPath"));
        return ApiResult.ok(null);
    }

    /** 复制文件 */
    @PostMapping("/copy")
    public ApiResult copyFile(@RequestBody Map<String, String> body) throws Exception {
        String oldPath = body.get("oldFullPath");
        String newPath = body.get("newFullPath");
        String content = new String(repositoryService.readFile(oldPath, null).readAllBytes());
        repositoryService.createFile(newPath, content, currentUser());
        return ApiResult.ok(null);
    }

    /** 锁定文件 */
    @PostMapping("/lock")
    public ApiResult lockFile(@RequestBody Map<String, String> body) throws Exception {
        repositoryService.lockPath(body.get("file"), currentUser());
        return ApiResult.ok(null);
    }

    /** 解锁文件 */
    @PostMapping("/unlock")
    public ApiResult unlockFile(@RequestBody Map<String, String> body) throws Exception {
        repositoryService.unlockPath(body.get("file"), currentUser());
        return ApiResult.ok(null);
    }

    /** 查看文件源码 */
    @GetMapping("/source")
    public ApiResult fileSource(@RequestParam String path) throws Exception {
        InputStream inputStream = repositoryService.readFile(path, null);
        String content = IOUtils.toString(inputStream, "utf-8");
        inputStream.close();
        String xml;
        try {
            Document doc = DocumentHelper.parseText(content);
            OutputFormat format = OutputFormat.createPrettyPrint();
            StringWriter out = new StringWriter();
            XMLWriter writer = new XMLWriter(out, format);
            writer.write(doc);
            xml = out.toString();
        } catch (Exception ex) {
            xml = content;
        }
        return ApiResult.ok(Map.of("content", xml));
    }

    /** 查看文件版本信息 */
    @GetMapping("/versions")
    public ApiResult fileVersions(@RequestParam String path) throws Exception {
        List<VersionFile> versions = repositoryService.getVersionFiles(path);
        return ApiResult.ok(versions);
    }
}
