package io.jsonfastlane;

public interface FastJsonReader<T> {
    T read(byte[] utf8Json);
}
