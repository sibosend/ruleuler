package com.caritasem.ruleuler.console.servlet;

import com.bstek.urule.console.servlet.ResourceLoaderServletHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 替换 bstek ResourceLoaderServletHandler，从外部目录加载静态资源。
 * urule-asserts 不打进 jar，必须通过 console.staticPath 指定外部路径。
 * 生产环境由 nginx 的 /urule/res/ location 直接 serve，Java 层不会收到这些请求。
 * 开发环境和 Docker 环境必须配置 console.staticPath。
 */
public class ExternalResourceLoaderServletHandler extends ResourceLoaderServletHandler {

    @Value("${console.staticPath}")
    private String staticPath;

    @Override
    public void execute(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getContextPath() + "/urule/res";
        String uri = req.getRequestURI();
        String resPath = uri.substring(path.length() + 1);

        File file = new File(staticPath, resPath);
        if (!file.exists() || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, resPath);
            return;
        }

        setContentType(resp, resPath);
        try (InputStream in = new FileInputStream(file);
             OutputStream out = resp.getOutputStream()) {
            IOUtils.copy(in, out);
            out.flush();
        }
    }

    private void setContentType(HttpServletResponse resp, String path) {
        if (path.endsWith(".js"))       resp.setContentType("text/javascript");
        else if (path.endsWith(".css")) resp.setContentType("text/css");
        else if (path.endsWith(".png")) resp.setContentType("image/png");
        else if (path.endsWith(".jpg")) resp.setContentType("image/jpeg");
        else                            resp.setContentType("application/octet-stream");
    }
}
