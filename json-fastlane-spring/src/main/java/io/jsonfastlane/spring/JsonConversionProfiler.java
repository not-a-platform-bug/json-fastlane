package io.jsonfastlane.spring;

import io.jsonfastlane.JsonFastlane;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class JsonConversionProfiler {
    private final JsonFastlane shapeProfiler = new JsonFastlane();
    private final Map<String, TimedConversionProfile> conversions = new ConcurrentHashMap<>();

    public void record(ConversionRoute route, byte[] utf8Json, long nanos) {
        shapeProfiler.record(route.endpoint(), utf8Json);
        recordConversion(route, utf8Json.length, nanos);
    }

    public void recordConversion(ConversionRoute route, long bytes, long nanos) {
        conversions.computeIfAbsent(route.key(), ignored -> new TimedConversionProfile(route))
            .record(bytes, nanos);
    }

    public Collection<ConversionProfileSnapshot> conversionSnapshots() {
        return conversions.values().stream()
            .map(TimedConversionProfile::snapshot)
            .sorted((left, right) -> {
                int endpoint = left.endpoint().compareTo(right.endpoint());
                if (endpoint != 0) {
                    return endpoint;
                }
                int direction = left.direction().compareTo(right.direction());
                if (direction != 0) {
                    return direction;
                }
                return left.javaType().compareTo(right.javaType());
            })
            .toList();
    }

    public JsonFastlane shapeProfiler() {
        return shapeProfiler;
    }

    private static final class TimedConversionProfile {
        private final ConversionRoute route;
        private final LongAdder samples = new LongAdder();
        private final LongAdder totalBytes = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();

        private TimedConversionProfile(ConversionRoute route) {
            this.route = route;
        }

        private void record(long bytes, long nanos) {
            samples.increment();
            totalBytes.add(bytes);
            totalNanos.add(nanos);
        }

        private ConversionProfileSnapshot snapshot() {
            long sampleCount = samples.sum();
            return new ConversionProfileSnapshot(
                route.endpoint(),
                route.direction(),
                route.javaType(),
                sampleCount,
                sampleCount == 0 ? 0 : totalBytes.sum() / sampleCount,
                sampleCount == 0 ? 0 : totalNanos.sum() / sampleCount
            );
        }
    }
}
