package com.caritasem.ruleuler.server.repository;

public enum StorageType {
    JCR, DB;

    public static StorageType fromString(String value) {
        if ("jcr".equalsIgnoreCase(value)) return JCR;
        if ("db".equalsIgnoreCase(value)) return DB;
        throw new IllegalArgumentException("Unknown storage type: " + value);
    }
}
