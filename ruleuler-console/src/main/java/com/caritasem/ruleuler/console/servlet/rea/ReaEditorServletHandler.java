package com.caritasem.ruleuler.console.servlet.rea;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.bstek.urule.Utils;
import com.bstek.urule.console.EnvironmentUtils;
import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.DatabaseResourceProvider;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.FileType;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.Type;
import com.bstek.urule.console.servlet.RenderPageServletHandler;
import com.bstek.urule.console.servlet.RequestContext;

/**
 * REA (Rule Expression Assistant) 编辑器 Servlet Handler。
 * 302 重定向到 ruleuler-admin 的 /admin/rea-editor 页面，
 * 并提供 loadProjectLibs 接口扫描项目下所有库文件路径。
 */
public class ReaEditorServletHandler extends RenderPageServletHandler {

    private RepositoryService repositoryService;
    private String adminBaseUrl;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String method = retriveMethod(req);
        if (method != null) {
            invokeMethod(method, req, resp);
        } else {
            String file = req.getParameter("file");
            String project = req.getParameter("project");
            if (project == null && file != null) {
                project = buildProjectNameFromFile(Utils.decodeURL(file));
            }
            String base = (adminBaseUrl != null && !adminBaseUrl.isEmpty())
                    ? adminBaseUrl
                    : req.getContextPath() + "/admin";
            StringBuilder url = new StringBuilder(base);
            url.append("/rea-editor?");
            if (file != null) {
                url.append("file=").append(URLEncoder.encode(file, "UTF-8"));
            }
            if (project != null) {
                if (file != null) url.append("&");
                url.append("project=").append(URLEncoder.encode(project, "UTF-8"));
            }
            resp.sendRedirect(url.toString());
        }
    }

    /**
     * 扫描项目下所有库文件路径，按类型分组返回 JSON。
     * 返回格式: {variable:[], constant:[], parameter:[], action:[]}
     */
    public void loadProjectLibs(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String project = req.getParameter("project");
        if (project == null || project.trim().isEmpty()) {
            throw new ServletException("parameter 'project' is required");
        }
        project = Utils.decodeURL(project);
        try {
            User user = EnvironmentUtils.getLoginUser(new RequestContext(req, resp));
            FileType[] libTypes = new FileType[]{
                    FileType.VariableLibrary,
                    FileType.ConstantLibrary,
                    FileType.ParameterLibrary,
                    FileType.ActionLibrary
            };
            Repository repo = repositoryService.loadRepository(project, user, false, libTypes, null);
            Map<String, List<String>> result = new HashMap<>();
            result.put("variable", new ArrayList<>());
            result.put("constant", new ArrayList<>());
            result.put("parameter", new ArrayList<>());
            result.put("action", new ArrayList<>());

            RepositoryFile rootFile = repo.getRootFile();
            if (rootFile != null && rootFile.getChildren() != null) {
                for (RepositoryFile projectFile : rootFile.getChildren()) {
                    collectLibPaths(projectFile, result);
                }
            }
            writeObjectToJson(resp, result);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void collectLibPaths(RepositoryFile file, Map<String, List<String>> result) {
        if (file == null) return;
        String name = file.getName();
        if (name != null && file.getType() != Type.folder && file.getType() != Type.project) {
            String fullPath = DatabaseResourceProvider.DBR + file.getFullPath();
            if (name.endsWith("." + FileType.VariableLibrary.toString())) {
                result.get("variable").add(fullPath);
            } else if (name.endsWith("." + FileType.ConstantLibrary.toString())) {
                result.get("constant").add(fullPath);
            } else if (name.endsWith("." + FileType.ParameterLibrary.toString())) {
                result.get("parameter").add(fullPath);
            } else if (name.endsWith("." + FileType.ActionLibrary.toString())) {
                result.get("action").add(fullPath);
            }
        }
        if (file.getChildren() != null) {
            for (RepositoryFile child : file.getChildren()) {
                collectLibPaths(child, result);
            }
        }
    }

    /**
     * 返回文件的原始 XML 内容（纯文本），供 REA 编辑器加载决策集。
     */
    public void loadRawXml(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String file = req.getParameter("file");
        if (file == null || file.trim().isEmpty()) {
            throw new ServletException("parameter 'file' is required");
        }
        file = Utils.decodeURL(file);
        // 去掉存储前缀
        if (file.startsWith(DatabaseResourceProvider.DBR)) {
            file = file.substring(DatabaseResourceProvider.DBR.length());
        }
        try (java.io.InputStream is = repositoryService.readFile(file, null)) {
            resp.setContentType("text/xml;charset=UTF-8");
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                resp.getOutputStream().write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public String url() {
        return "/reaeditor";
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setAdminBaseUrl(String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }
}
