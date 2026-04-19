package com.bstek.urule.runtime.shadow;

import java.util.ArrayList;
import java.util.List;

/**
 * ThreadLocal 容器，存储当前 session 中影子规则的命中列表。
 */
public final class ShadowContext {
    private static final ThreadLocal<List<ShadowHitInfo>> HITS = ThreadLocal.withInitial(ArrayList::new);

    public static void addHit(ShadowHitInfo hit) {
        HITS.get().add(hit);
    }

    public static List<ShadowHitInfo> getHits() {
        return HITS.get();
    }

    public static void clear() {
        HITS.remove();
    }
}
