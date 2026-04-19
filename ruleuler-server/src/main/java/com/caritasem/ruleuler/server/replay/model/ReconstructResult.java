package com.caritasem.ruleuler.server.replay.model;

import java.util.List;
import java.util.Map;

public record ReconstructResult(
    Map<String, Map<String, Object>> input,
    Map<String, Map<String, Object>> output,
    List<String> missingCategories,
    List<String> missingVariables,
    List<String> filledVariables,
    String completenessStatus
) {}
