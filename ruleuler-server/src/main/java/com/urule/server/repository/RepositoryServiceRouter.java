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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

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
        // importXml 没有明确的项目上下文，暂时委托给 jcrDelegate
        jcrDelegate.importXml(inputStream, overwrite);
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
    }
}
