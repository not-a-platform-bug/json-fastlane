package io.jsonfastlane;

import java.util.Map;

public record FieldProfileSnapshot(
    String name,
    long occurrences,
    double averagePosition,
    Map<JsonValueKind, Long> valueKinds
) {
}
