package io.jsonfastlane;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

final class FieldProfile {
    private final String name;
    private final LongAdder occurrences = new LongAdder();
    private final LongAdder totalPosition = new LongAdder();
    private final Map<JsonValueKind, LongAdder> kindCounts = new EnumMap<>(JsonValueKind.class);

    FieldProfile(String name) {
        this.name = name;
        for (JsonValueKind kind : JsonValueKind.values()) {
            kindCounts.put(kind, new LongAdder());
        }
    }

    synchronized void record(JsonValueKind kind, int position) {
        occurrences.increment();
        totalPosition.add(position);
        kindCounts.get(kind).increment();
    }

    FieldProfileSnapshot snapshot() {
        long count = occurrences.sum();
        Map<JsonValueKind, Long> kinds = new EnumMap<>(JsonValueKind.class);
        for (Map.Entry<JsonValueKind, LongAdder> entry : kindCounts.entrySet()) {
            long value = entry.getValue().sum();
            if (value > 0) {
                kinds.put(entry.getKey(), value);
            }
        }

        return new FieldProfileSnapshot(
            name,
            count,
            count == 0 ? 0 : (double) totalPosition.sum() / count,
            kinds
        );
    }
}
