package io.jsonfastlane;

import java.util.List;

public record EndpointProfileSnapshot(
    String endpoint,
    long samples,
    long averagePayloadBytes,
    long rootObjects,
    long rootArrays,
    List<FieldProfileSnapshot> fields,
    List<FieldOrderSnapshot> fieldOrders
) {
}
