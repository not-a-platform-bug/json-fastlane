package io.jsonfastlane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class EndpointProfile {
    private final String endpoint;
    private final LongAdder samples = new LongAdder();
    private final LongAdder totalBytes = new LongAdder();
    private final LongAdder rootObjects = new LongAdder();
    private final LongAdder rootArrays = new LongAdder();
    private final Map<String, FieldProfile> fields = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> fieldOrders = new ConcurrentHashMap<>();

    EndpointProfile(String endpoint) {
        this.endpoint = endpoint;
    }

    void record(JsonShapeObservation observation) {
        samples.increment();
        totalBytes.add(observation.payloadBytes());

        if (observation.rootKind() == JsonValueKind.OBJECT) {
            rootObjects.increment();
        } else if (observation.rootKind() == JsonValueKind.ARRAY) {
            rootArrays.increment();
        }

        if (!observation.fields().isEmpty()) {
            fieldOrders.computeIfAbsent(observation.fieldOrderSignature(), ignored -> new LongAdder()).increment();
        }

        for (FieldObservation field : observation.fields()) {
            fields.computeIfAbsent(field.name(), FieldProfile::new).record(field.kind(), field.position());
        }
    }

    public EndpointProfileSnapshot snapshot() {
        List<FieldProfileSnapshot> fieldSnapshots = fields.values().stream()
            .map(FieldProfile::snapshot)
            .sorted(Comparator.comparing(FieldProfileSnapshot::name))
            .toList();

        List<FieldOrderSnapshot> orderSnapshots = new ArrayList<>();
        for (Map.Entry<String, LongAdder> entry : fieldOrders.entrySet()) {
            orderSnapshots.add(new FieldOrderSnapshot(entry.getKey(), entry.getValue().sum()));
        }
        orderSnapshots.sort(Comparator.comparingLong(FieldOrderSnapshot::samples).reversed());

        long sampleCount = samples.sum();
        long byteCount = totalBytes.sum();
        return new EndpointProfileSnapshot(
            endpoint,
            sampleCount,
            sampleCount == 0 ? 0 : byteCount / sampleCount,
            rootObjects.sum(),
            rootArrays.sum(),
            fieldSnapshots,
            orderSnapshots
        );
    }
}
