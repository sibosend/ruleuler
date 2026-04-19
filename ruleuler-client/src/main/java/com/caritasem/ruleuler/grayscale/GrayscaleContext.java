package com.caritasem.ruleuler.grayscale;

import java.util.Map;

/**
 * 灰度路由上下文（ThreadLocal），每次请求设置，请求结束清除。
 */
public class GrayscaleContext {

    private static final ThreadLocal<ContextInfo> HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ROUTED_TO_GRAY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SKIP_GRAYSCALE = new ThreadLocal<>();

    public record ContextInfo(String routingKey, Map<String, Object> attributes) {}

    public static void set(ContextInfo info) { HOLDER.set(info); }
    public static ContextInfo get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); ROUTED_TO_GRAY.remove(); SKIP_GRAYSCALE.remove(); }

    public static void markRoutedToGray(boolean toGray) { ROUTED_TO_GRAY.set(toGray); }
    public static Boolean wasRoutedToGray() { return ROUTED_TO_GRAY.get(); }

    /** 标记当前请求跳过灰度路由（用于回放等场景） */
    public static void skipGrayscale() { SKIP_GRAYSCALE.set(true); }
    public static boolean shouldSkipGrayscale() { return Boolean.TRUE.equals(SKIP_GRAYSCALE.get()); }
}
