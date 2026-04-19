package com.caritasem.ruleuler.server.replay.model;

import java.util.List;

public record DiffResult(
    boolean match,
    List<FieldDiff> fields
) {}
