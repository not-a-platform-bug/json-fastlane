package io.jsonfastlane;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonFastlane {
    private final Map<String, EndpointProfile> profiles = new ConcurrentHashMap<>();

    public void record(String endpoint, byte[] utf8Json) {
        JsonShapeObservation observation = JsonShapeScanner.scan(utf8Json);
        profiles.computeIfAbsent(endpoint, EndpointProfile::new).record(observation);
    }

    public void record(String endpoint, String json) {
        record(endpoint, json.getBytes(StandardCharsets.UTF_8));
    }

    public Collection<EndpointProfileSnapshot> snapshots() {
        return profiles.values().stream()
            .map(EndpointProfile::snapshot)
            .sorted((left, right) -> left.endpoint().compareTo(right.endpoint()))
            .toList();
    }
}
