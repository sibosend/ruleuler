package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.builder.KnowledgeBase;
import com.bstek.urule.builder.KnowledgeBuilder;
import com.bstek.urule.builder.ResourceBase;
import com.bstek.urule.builder.resource.Resource;
import com.bstek.urule.runtime.KnowledgePackage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 从 snapshot 内容构建 KnowledgePackage。
 * 通过 SnapshotResourceProvider（ThreadLocal）注入资源，使 dbr: 路径可解析。
 */
@Component
public class SnapshotPackageBuilder {

    private final KnowledgeBuilder knowledgeBuilder;

    public SnapshotPackageBuilder(@Qualifier("urule.knowledgeBuilder") KnowledgeBuilder knowledgeBuilder) {
        this.knowledgeBuilder = knowledgeBuilder;
    }

    @SuppressWarnings("unchecked")
    public KnowledgePackage build(Map<String, String> snapshotContent) throws Exception {
        SnapshotResourceProvider.setSnapshot(snapshotContent);
        try {
            ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
            java.lang.reflect.Field resourcesField = ResourceBase.class.getDeclaredField("resources");
            resourcesField.setAccessible(true);
            List<Resource> resources = (List<Resource>) resourcesField.get(resourceBase);
            for (Map.Entry<String, String> entry : snapshotContent.entrySet()) {
                String path = entry.getKey();
                String xml = entry.getValue();
                if (path == null || xml == null || xml.isBlank()) continue;
                String resourcePath = path.startsWith("dbr:") ? path : "dbr:" + path;
                resources.add(new Resource(xml, resourcePath));
            }
            KnowledgeBase kb = knowledgeBuilder.buildKnowledgeBase(resourceBase);
            return kb.getKnowledgePackage();
        } finally {
            SnapshotResourceProvider.clearSnapshot();
        }
    }
}
