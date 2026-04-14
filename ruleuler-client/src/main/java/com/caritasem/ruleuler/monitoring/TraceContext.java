package com.caritasem.ruleuler.monitoring;

/**
 * ThreadLocal 持有当前执行的追踪上下文，供 TraceDebugWriter 读取。
 */
public final class TraceContext {

    private static final ThreadLocal<TraceInfo> HOLDER = new ThreadLocal<>();

    public record TraceInfo(
            String executionId,
            String project,
            String packageId,
            String flowId
    ) {}

    public static void set(TraceInfo info) { HOLDER.set(info); }
    public static TraceInfo get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    private TraceContext() {}
}
