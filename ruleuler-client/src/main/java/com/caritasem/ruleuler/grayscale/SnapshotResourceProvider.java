package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.builder.resource.Resource;
import com.bstek.urule.builder.resource.ResourceProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Client 端 snapshot 资源提供者，处理 dbr: 前缀。
 * 在 GrayscaleRoutingController.buildFromSnapshot() 构建灰度包时，
 * 通过 ThreadLocal 注入 snapshot 内容，使 KnowledgeBuilder 能解析 dbr: 路径引用。
 */
@Component
public class SnapshotResourceProvider implements ResourceProvider {

    public static final String DBR = "dbr:";

    private static final ThreadLocal<Map<String, String>> SNAPSHOT_HOLDER = new ThreadLocal<>();

    public static void setSnapshot(Map<String, String> snapshot) {
        SNAPSHOT_HOLDER.set(snapshot);
    }

    public static void clearSnapshot() {
        SNAPSHOT_HOLDER.remove();
    }

    @Override
    public Resource provide(String path, String version) {
        Map<String, String> snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot == null) {
            throw new com.bstek.urule.RuleException("No snapshot context for path: " + path);
        }
        // 去掉 dbr: 前缀查找
        String lookupPath = path.startsWith(DBR) ? path.substring(DBR.length()) : path;
        String content = snapshot.get(lookupPath);
        if (content == null) {
            // 也尝试带 dbr: 前缀的 key
            content = snapshot.get(path);
        }
        if (content == null) {
            throw new com.bstek.urule.RuleException("Snapshot resource not found: " + path);
        }
        return new Resource(content, path);
    }

    @Override
    public boolean support(String path) {
        return path != null && path.startsWith(DBR);
    }
}
