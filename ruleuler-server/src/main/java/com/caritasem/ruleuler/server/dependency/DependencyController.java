package com.caritasem.ruleuler.server.dependency;

import com.caritasem.ruleuler.server.auth.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dependencies")
@RequiredArgsConstructor
public class DependencyController {

    private final DependencyService dependencyService;

    @GetMapping("/analyze")
    public ApiResult analyze(@RequestParam String path) {
        if (path == null || path.isBlank()) {
            return ApiResult.error(400, "path 参数不能为空");
        }
        return ApiResult.ok(dependencyService.analyze(path));
    }
}
