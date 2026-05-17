# json-fastlane

[한국어](README.ko.md)

`json-fastlane` is a JVM experiment for profile-guided JSON serialization.

The idea is not to replace Jackson on day one. The first goal is to observe
real API payload shapes, identify endpoints where JSON is expensive, then route
hot DTOs through generated fast-path codecs with a safe fallback.

## Current prototype

- Records top-level JSON field shape per endpoint.
- Tracks field order, field value kinds, payload size, and sample count.
- Includes generated-style UTF-8 reader and writer examples with no reflection.
- Includes a reusable UTF-8 buffer writer path for lower-allocation response serialization.
- Includes a Spring MVC Jackson profiling converter prototype.

## Modules

- `json-fastlane-core`: dependency-light profiling, reader/writer contracts,
  fallback tracking, UTF-8 buffer, and generated-style examples.
- `json-fastlane-spring`: Spring MVC converters, generated writer registry, and
  Jackson fallback/profiling integration.
- `json-fastlane-netty`: Netty `ByteBuf` writer scaffold for pooled-buffer
  WebFlux/Reactor Netty style integrations.
- `json-fastlane-benchmarks`: smoke checks, realistic load simulation, JFR run,
  and JMH benchmarks.

## Run

```bash
./gradlew :json-fastlane-core:run
./gradlew check
./gradlew realisticLoadTest
./gradlew realisticLoadTest -PloadThreads=16 -PloadIterations=50000
./gradlew jfrRealisticLoadTest
./gradlew jmh -PjmhWarmups=1 -PjmhIterations=1 -PjmhForks=1
```

## Spring MVC adapter

The prototype includes a profiling Jackson converter:

```java
JsonConversionProfiler profiler = new JsonConversionProfiler();
FastJsonWriterRegistry writerRegistry = new FastJsonWriterRegistry();
ProfilingJackson2HttpMessageConverter converter =
    new ProfilingJackson2HttpMessageConverter(objectMapper, profiler, writerRegistry);

writerRegistry.register(OrderSummaryResponse.class, new OrderSummaryResponseWriter());
```

If a registered `FastJsonBufferWriter` exists for the response type, the
converter writes through:

```text
DTO -> reusable Utf8JsonBuffer -> response OutputStream
```

Otherwise, it falls back to Jackson. In a Spring MVC app, replace the first
Jackson converter during Web MVC setup:

```java
@Configuration
class JsonFastlaneWebConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;
    private final JsonConversionProfiler profiler = new JsonConversionProfiler();
    private final FastJsonWriterRegistry writerRegistry = new FastJsonWriterRegistry();

    JsonFastlaneWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.writerRegistry.register(OrderSummaryResponse.class, new OrderSummaryResponseWriter());
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        SpringJsonFastlaneConfigurer.replaceFirstJacksonConverter(converters, profiler, writerRegistry);
    }
}
```

An interceptor or filter should set the current endpoint around request handling:

```java
try (JsonFastlaneSpring.EndpointScope ignored =
         JsonFastlaneSpring.withCurrentEndpoint("/orders")) {
    // Spring request handling continues here.
}
```

The converter records:

- read/write direction
- endpoint
- Java type
- average payload bytes
- average conversion time
- JSON field shape through the core profiler

## Realistic load simulation

`realisticLoadTest` runs an in-process load model for two API shapes:

- `POST /checkout`: nested request JSON with cart items, address, coupon, and trace id.
- `GET /orders/{id}/summary`: nested response JSON with 12 order lines, shipping, payment, timeline, and memo.

It compares:

- Jackson deserialization
- profiling Spring converter deserialization
- generated-style fast reader deserialization
- Jackson serialization
- profiling Spring converter serialization
- generated-style fast writer serialization
- generated-style reusable-buffer writer serialization
- Spring converter direct generated writer serialization

The task prints throughput, p50/p95/p99 latency, converter profiling snapshots,
and observed JSON field shapes. The generated writer output is also parsed and
compared with Jackson JSON before the load test starts.

## Direction

1. Generate DTO-specific readers/writers from observed endpoint shapes.
2. Export endpoint JSON cost reports through logs, Micrometer, and JFR events.
3. Add fallback-rate tracking for generated readers.
4. Keep Jackson as fallback for unknown shapes and compatibility.
