package io.jsonfastlane.spring;

import io.jsonfastlane.FastJsonBufferWriter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FastJsonWriterRegistry {
    private static final FastJsonBufferWriter<Object> NO_WRITER = (value, out) -> {
        throw new IllegalStateException("No generated writer registered");
    };

    private final Map<Class<?>, FastJsonBufferWriter<?>> writers = new ConcurrentHashMap<>();
    private final Map<Class<?>, FastJsonBufferWriter<?>> lookupCache = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, FastJsonBufferWriter<? super T> writer) {
        writers.put(type, writer);
        lookupCache.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> FastJsonBufferWriter<T> find(Class<T> type) {
        FastJsonBufferWriter<?> cached = lookupCache.get(type);
        if (cached != null) {
            if (cached == NO_WRITER) {
                return null;
            }
            return (FastJsonBufferWriter<T>) cached;
        }

        FastJsonBufferWriter<?> exact = writers.get(type);
        if (exact != null) {
            lookupCache.put(type, exact);
            return (FastJsonBufferWriter<T>) exact;
        }

        for (Map.Entry<Class<?>, FastJsonBufferWriter<?>> entry : writers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                FastJsonBufferWriter<?> writer = entry.getValue();
                lookupCache.put(type, writer);
                return (FastJsonBufferWriter<T>) writer;
            }
        }
        lookupCache.put(type, NO_WRITER);
        return null;
    }
}
