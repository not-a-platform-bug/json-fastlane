package io.jsonfastlane.spring;

public record ConversionRoute(
    String endpoint,
    ConversionDirection direction,
    String javaType
) {
    String key() {
        return endpoint + "|" + direction + "|" + javaType;
    }
}
