package io.jsonfastlane.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;

public final class SpringJsonFastlaneConfigurer {
    private SpringJsonFastlaneConfigurer() {
    }

    public static ProfilingJackson2HttpMessageConverter profilingJacksonConverter(
        ObjectMapper objectMapper,
        JsonConversionProfiler profiler
    ) {
        return new ProfilingJackson2HttpMessageConverter(objectMapper, profiler);
    }

    public static ProfilingJackson2HttpMessageConverter profilingJacksonConverter(
        ObjectMapper objectMapper,
        JsonConversionProfiler profiler,
        FastJsonWriterRegistry writerRegistry
    ) {
        return new ProfilingJackson2HttpMessageConverter(objectMapper, profiler, writerRegistry);
    }

    public static FastJsonHttpMessageConverter fastJsonConverter(
        JsonConversionProfiler profiler,
        FastJsonWriterRegistry writerRegistry
    ) {
        return new FastJsonHttpMessageConverter(profiler, writerRegistry);
    }

    public static void addFastJsonConverterFirst(
        List<HttpMessageConverter<?>> converters,
        JsonConversionProfiler profiler,
        FastJsonWriterRegistry writerRegistry
    ) {
        converters.add(0, new FastJsonHttpMessageConverter(profiler, writerRegistry));
    }

    public static boolean replaceFirstJacksonConverter(
        List<HttpMessageConverter<?>> converters,
        JsonConversionProfiler profiler
    ) {
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                converters.set(i, new ProfilingJackson2HttpMessageConverter(
                    jacksonConverter.getObjectMapper(),
                    profiler
                ));
                return true;
            }
        }
        return false;
    }

    public static boolean replaceFirstJacksonConverter(
        List<HttpMessageConverter<?>> converters,
        JsonConversionProfiler profiler,
        FastJsonWriterRegistry writerRegistry
    ) {
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                converters.set(i, new ProfilingJackson2HttpMessageConverter(
                    jacksonConverter.getObjectMapper(),
                    profiler,
                    writerRegistry
                ));
                return true;
            }
        }
        return false;
    }
}
