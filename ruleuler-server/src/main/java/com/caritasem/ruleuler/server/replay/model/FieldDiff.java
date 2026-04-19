package com.caritasem.ruleuler.server.replay.model;

public record FieldDiff(
    String category,
    String name,
    String status,
    Object oldValue,
    Object newValue
) {}
