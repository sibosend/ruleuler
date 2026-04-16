package com.caritasem.ruleuler.server.grayscale;

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
 * 从快照内容（path → xmlContent）直接 build KnowledgePackage。
 * 提取自 ApprovalService.buildFromSnapshot()，供 ApprovalService 和 GrayscaleService 共用。
 */
@Component
public class SnapshotKnowledgeBuilder {

    private final KnowledgeBuilder knowledgeBuilder;

    public SnapshotKnowledgeBuilder(@Qualifier("urule.knowledgeBuilder") KnowledgeBuilder knowledgeBuilder) {
        this.knowledgeBuilder = knowledgeBuilder;
    }

    /**
     * 用快照内容直接 build KnowledgePackage，不走 repositoryService。
     */
    @SuppressWarnings("unchecked")
    public KnowledgePackage build(Map<String, String> snapshotContent) throws Exception {
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        java.lang.reflect.Field resourcesField = ResourceBase.class.getDeclaredField("resources");
        resourcesField.setAccessible(true);
        List<Resource> resources = (List<Resource>) resourcesField.get(resourceBase);
        for (Map.Entry<String, String> entry : snapshotContent.entrySet()) {
            String path = entry.getKey();
            String xml = entry.getValue();
            if (xml == null || xml.isBlank()) continue;
            String resourcePath = path.startsWith("dbr:") ? path : "dbr:" + path;
            resources.add(new Resource(xml, resourcePath));
        }
        KnowledgeBase kb = knowledgeBuilder.buildKnowledgeBase(resourceBase);
        return kb.getKnowledgePackage();
    }
}
