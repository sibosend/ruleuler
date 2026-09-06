package com.caritasem.ruleuler.server.function;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{project}/function-jars")
public class FunctionJarController {

    private final FunctionJarService service;
    private final FunctionStatusDao statusDao;

    public FunctionJarController(FunctionJarService service, FunctionStatusDao statusDao) {
        this.service = service;
        this.statusDao = statusDao;
    }

    @PostMapping
    @RequirePermission("project:function:upload")
    public ApiResult upload(@PathVariable String project, @RequestParam("file") MultipartFile file) throws Exception {
        String operator = AuthContext.get().getUsername();
        return ApiResult.ok(service.upload(project, file, operator));
    }

    @GetMapping
    @RequirePermission("project:function:view")
    public ApiResult list(@PathVariable String project) {
        return ApiResult.ok(service.list(project));
    }

    @GetMapping("/status")
    @RequirePermission("project:function:view")
    public ApiResult status(@PathVariable String project) {
        return ApiResult.ok(statusDao.listByProject(project));
    }
}
