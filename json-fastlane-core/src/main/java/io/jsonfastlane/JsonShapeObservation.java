package io.jsonfastlane;

import java.util.List;

record JsonShapeObservation(
    int payloadBytes,
    JsonValueKind rootKind,
    List<FieldObservation> fields,
    String fieldOrderSignature
) {
}
