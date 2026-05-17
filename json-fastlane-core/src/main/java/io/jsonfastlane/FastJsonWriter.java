package io.jsonfastlane;

public interface FastJsonWriter<T> {
    byte[] write(T value);
}
