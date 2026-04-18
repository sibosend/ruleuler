package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.repository.StorageContext;
import com.caritasem.ruleuler.server.repository.StorageType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final RepositoryService repositoryService;

    public ProjectController(@Qualifier("urule.repositoryService") RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping
    public ApiResult list() throws Exception {
        List<RepositoryFile> projects = repositoryService.loadProjects(null);
        return ApiResult.ok(projects);
    }

    @PostMapping
    public ApiResult create(@RequestBody Map<String, String> body) throws Exception {
        String name = body.get("name");
        String storageType = body.getOrDefault("storageType", "db");
        StorageContext.set(StorageType.fromString(storageType));
        try {
            RepositoryFile result = repositoryService.createProject(name, null, true);
            return ApiResult.ok(result);
        } finally {
            StorageContext.clear();
        }
    }

    @DeleteMapping("/{name}")
    public ApiResult delete(@PathVariable String name) throws Exception {
        repositoryService.deleteFile("/" + name, null);
        return ApiResult.ok(null);
    }

    @GetMapping("/{name}/export")
    public void export(@PathVariable String name, HttpServletResponse resp) throws Exception {
        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".zip\"");
        try (ZipOutputStream zos = new ZipOutputStream(resp.getOutputStream())) {
            zos.putNextEntry(new ZipEntry(name + ".dat"));
            repositoryService.exportXml("/" + name, zos);
            zos.closeEntry();
            zos.finish();
        }
    }

    @PostMapping("/import")
    public ApiResult importProject(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "overwrite", defaultValue = "true") boolean overwrite) throws Exception {
        byte[] data = file.getBytes();
        // ZIP magic: PK(0x504B)，否则视为原始文件（JCR XML 或 DBR JSON）
        if (data.length >= 2 && data[0] == 0x50 && data[1] == 0x4B) {
            try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
                zis.getNextEntry();
                repositoryService.importXml(zis, overwrite);
            }
        } else {
            repositoryService.importXml(new java.io.ByteArrayInputStream(data), overwrite);
        }
        return ApiResult.ok(null);
    }

    @GetMapping("/{name}/packages")
    public ApiResult listPackages(@PathVariable String name) throws Exception {
        List<ResourcePackage> packages = repositoryService.loadProjectResourcePackages(name);
        return ApiResult.ok(packages);
    }
}
