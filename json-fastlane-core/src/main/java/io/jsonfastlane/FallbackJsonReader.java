package io.jsonfastlane;

import java.util.concurrent.atomic.LongAdder;

public final class FallbackJsonReader<T> implements FastJsonReader<T> {
    private final FastJsonReader<T> fastReader;
    private final FastJsonReader<T> fallbackReader;
    private final LongAdder fastReads = new LongAdder();
    private final LongAdder fallbackReads = new LongAdder();

    public FallbackJsonReader(FastJsonReader<T> fastReader, FastJsonReader<T> fallbackReader) {
        this.fastReader = fastReader;
        this.fallbackReader = fallbackReader;
    }

    @Override
    public T read(byte[] utf8Json) {
        try {
            T value = fastReader.read(utf8Json);
            fastReads.increment();
            return value;
        } catch (RuntimeException exception) {
            fallbackReads.increment();
            return fallbackReader.read(utf8Json);
        }
    }

    public ReaderFallbackSnapshot snapshot() {
        return new ReaderFallbackSnapshot(fastReads.sum(), fallbackReads.sum());
    }
}
