package io.jsonfastlane.spring;

public record ConversionProfileSnapshot(
    String endpoint,
    ConversionDirection direction,
    String javaType,
    long samples,
    long averagePayloadBytes,
    long averageNanos
) {
}
