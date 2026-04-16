package com.caritasem.ruleuler.grayscale;

import java.util.Map;

/**
 * 灰度路由上下文（ThreadLocal），每次请求设置，请求结束清除。
 */
public class GrayscaleContext {

    private static final ThreadLocal<ContextInfo> HOLDER = new ThreadLocal<>();

    public record ContextInfo(String routingKey, Map<String, Object> attributes) {}

    public static void set(ContextInfo info) { HOLDER.set(info); }
    public static ContextInfo get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
