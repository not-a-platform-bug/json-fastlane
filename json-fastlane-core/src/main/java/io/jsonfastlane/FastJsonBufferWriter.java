package io.jsonfastlane;

public interface FastJsonBufferWriter<T> {
    void write(T value, Utf8JsonBuffer out);
}
