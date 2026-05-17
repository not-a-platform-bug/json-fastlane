package io.jsonfastlane;

public interface TryFastJsonReader<T> {
    T tryRead(byte[] utf8Json);
}
