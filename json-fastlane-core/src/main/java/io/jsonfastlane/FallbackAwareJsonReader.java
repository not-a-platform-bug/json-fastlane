package io.jsonfastlane;

import java.util.concurrent.atomic.LongAdder;

public final class FallbackAwareJsonReader<T> implements FastJsonReader<T> {
    private final TryFastJsonReader<T> fastReader;
    private final FastJsonReader<T> fallbackReader;
    private final LongAdder fastReads = new LongAdder();
    private final LongAdder fallbackReads = new LongAdder();

    public FallbackAwareJsonReader(TryFastJsonReader<T> fastReader, FastJsonReader<T> fallbackReader) {
        this.fastReader = fastReader;
        this.fallbackReader = fallbackReader;
    }

    @Override
    public T read(byte[] utf8Json) {
        T value = fastReader.tryRead(utf8Json);
        if (value != null) {
            fastReads.increment();
            return value;
        }

        fallbackReads.increment();
        return fallbackReader.read(utf8Json);
    }

    public ReaderFallbackSnapshot snapshot() {
        return new ReaderFallbackSnapshot(fastReads.sum(), fallbackReads.sum());
    }
}
