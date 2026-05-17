package io.jsonfastlane.spring;

import io.jsonfastlane.FastJsonBufferWriter;
import io.jsonfastlane.Utf8JsonBuffer;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;

public final class FastJsonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {
    private final JsonConversionProfiler profiler;
    private final FastJsonWriterRegistry writerRegistry;
    private final ThreadLocal<Utf8JsonBuffer> writerBuffers = ThreadLocal.withInitial(() -> new Utf8JsonBuffer(2048));
    private volatile Class<?> cachedWriterType;
    private volatile FastJsonBufferWriter<?> cachedWriter;

    public FastJsonHttpMessageConverter(
        JsonConversionProfiler profiler,
        FastJsonWriterRegistry writerRegistry
    ) {
        super(MediaType.APPLICATION_JSON);
        this.profiler = profiler;
        this.writerRegistry = writerRegistry;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return writerFor(clazz) != null;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
        throws IOException, HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException("json-fastlane generated converter is write-only", inputMessage);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {
        FastJsonBufferWriter writer = writerFor(object.getClass());
        if (writer == null) {
            throw new HttpMessageNotWritableException("No generated JSON writer registered for " + object.getClass());
        }

        Utf8JsonBuffer out = writerBuffers.get().reset();
        long start = System.nanoTime();
        writer.write(object, out);
        long elapsed = System.nanoTime() - start;

        outputMessage.getHeaders().setContentLength(out.size());
        out.writeTo(outputMessage.getBody());

        profiler.recordConversion(new ConversionRoute(
            JsonFastlaneSpring.currentEndpoint(),
            ConversionDirection.WRITE,
            object.getClass().getName()
        ), out.size(), elapsed);
    }

    private FastJsonBufferWriter<?> writerFor(Class<?> type) {
        if (type == cachedWriterType) {
            return cachedWriter;
        }

        FastJsonBufferWriter<?> writer = writerRegistry.find(type);
        if (writer != null) {
            cachedWriterType = type;
            cachedWriter = writer;
        }
        return writer;
    }
}
