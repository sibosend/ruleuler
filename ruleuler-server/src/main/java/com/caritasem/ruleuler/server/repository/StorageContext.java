package com.caritasem.ruleuler.server.repository;

public final class StorageContext {

    private static final ThreadLocal<StorageType> HOLDER = new ThreadLocal<>();

    private StorageContext() {}

    public static void set(StorageType type) {
        HOLDER.set(type);
    }

    public static StorageType get() {
        StorageType type = HOLDER.get();
        if (type == null) {
            throw new IllegalStateException("StorageType not set in current thread");
        }
        return type;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
