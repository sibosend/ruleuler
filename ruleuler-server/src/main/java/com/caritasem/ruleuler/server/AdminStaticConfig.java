package com.caritasem.ruleuler.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve ruleuler-admin 前端静态文件。
 * 生产环境由 nginx 处理，admin.staticPath 为空时不注册。
 */
@Configuration
public class AdminStaticConfig implements WebMvcConfigurer {

    @Value("${admin.staticPath:}")
    private String staticPath;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (staticPath == null || staticPath.isBlank()) return;
        registry.addRedirectViewController("/admin", "/admin/index.html");
        registry.addRedirectViewController("/admin/", "/admin/index.html");
        registry.addRedirectViewController("/favicon.ico", "/admin/favicon.ico");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (staticPath == null || staticPath.isBlank()) return;

        String location = staticPath.endsWith("/") ? staticPath : staticPath + "/";

        registry.addResourceHandler("/admin/**")
                .addResourceLocations("file:" + location)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) return resource;
                        // SPA fallback → index.html
                        return new FileSystemResource(staticPath + "/index.html");
                    }
                });
    }
}
