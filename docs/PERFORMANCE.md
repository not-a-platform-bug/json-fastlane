# Performance Validation

[한국어](PERFORMANCE.ko.md)

The first repeatable performance check is `realisticLoadTest`.

```bash
./gradlew realisticLoadTest
```

Default model:

- 8 worker threads
- 12,000 iterations per worker per scenario
- 479 byte checkout request
- 1,764 byte order summary response
- blackhole consumption enabled so returned objects and byte arrays are used
- reusable-buffer writer scenario included to estimate a lower-allocation
  response path that avoids the final `byte[]` copy
- Spring direct writer scenario included to measure the actual
  `HttpMessageConverter` path using a registered `FastJsonBufferWriter`

Tune the run:

```bash
./gradlew realisticLoadTest -PloadThreads=16 -PloadIterations=50000
```

Capture a JFR profile:

```bash
./gradlew jfrRealisticLoadTest
```

The recording is written to:

```text
build/reports/json-fastlane/realistic-load.jfr
```

Run JMH:

```bash
./gradlew jmh
./gradlew jmh -PjmhInclude=io.jsonfastlane.bench.JsonFastPathBenchmark.fastlaneWriteNettyByteBuf
```

All executable performance tasks live in `json-fastlane-benchmarks`, but Gradle
can run them from the root by task name.

Latest local result on this workspace:

```text
scenario                                      ops/s       p50 us       p95 us       p99 us
jackson-read-checkout                       1906034         1.71         8.83        21.00
spring-default-read-checkout                1382506         2.38        10.38        20.83
profiling-converter-read-checkout            754802         5.83        25.75        79.38
fastlane-generated-read-checkout            2114481         1.04         8.67        14.63
jackson-write-summary                       1609938         2.04         9.96        30.88
spring-default-write-summary                1659146         2.54         7.13        16.17
profiling-converter-write-summary            417165         9.46        31.58       141.29
fastlane-generated-write-summary            3815398         1.13         2.58         3.42
fastlane-reused-buffer-write-summary        4097266         1.04         2.38         3.38
fastlane-spring-direct-write-summary        2036580         2.50         5.42         8.67
fastlane-spring-direct-sink-summary         2033478         2.50         4.75         7.42
fastlane-dedicated-sink-summary             2022223         2.33         4.92         9.13
```

Current readout:

- The profiling converter is intentionally slower than plain Jackson because it
  copies the body, records timings, and scans JSON shape.
- The generated-style checkout reader is faster than Jackson on this stable
  request shape: 1.11x throughput versus raw Jackson and 1.53x versus Spring
  default conversion in the latest longer local run.
- The generated-style response writer is faster than Jackson on this nested
  response shape: 2.37x throughput in the latest longer local run when returning a
  copied `byte[]`.
- The reusable-buffer response writer is much faster than Jackson in this
  simulation: 2.54x throughput in the latest longer local run. This is not an
  apples-to-apples replacement for `writeValueAsBytes`; it represents the
  direction a server integration should take by writing into a reusable response
  buffer or stream and avoiding the final copy.
- The Spring direct writer scenario validates that the server-facing converter
  can use the same generated writer registry and write into the response body
  without creating the intermediate Jackson output buffer.
- The dedicated generated writer converter is thinner than the Jackson-derived
  profiling converter and reached 1.22x Spring default write throughput in the
  latest longer local run.

Recent JVM-oriented changes:

- The generated writer registry caches class-to-writer lookups and avoids
  per-write `Optional` allocation.
- `Utf8JsonBuffer.writeString` has an ASCII no-escape fast path, which matches
  the common service payload case.
- Control-character unicode escapes are written directly instead of using
  `String.format`.
- The generated reader cursor compares static JSON fragments in a
  bounds-check-friendly shape before advancing the cursor.
- `FallbackJsonReader` tracks fast-path and fallback reads for uncommon payload
  shapes.
- `FallbackAwareJsonReader` avoids exception-driven fallback by using
  `TryFastJsonReader.tryRead`, which is much cheaper when an uncommon shape is
  expected and valid.
- `NettyJsonBuffer` and `FastJsonByteBufWriter` provide a first pooled-buffer
  path for Netty/WebFlux-style integrations.
- JMH benchmarks are available under `src/jmh/java`.

Latest fallback-path comparison with a shuffled request shape:

```text
fastlane-fallback-read-shuffled          904318 ops/s
fastlane-aware-fallback-read-shuffled   2039685 ops/s
```

Both scenarios fall back 100% of the time for the shuffled shape. The difference
is that the fallback-aware reader detects the mismatch without throwing an
exception on the normal fallback path.
- The test is a load simulation, not a replacement for JMH or production
  profiling, so exact numbers vary between runs. It is meant to keep the project
  honest while the codec idea evolves.

Completed performance work:

- Realistic load simulation.
- Spring default baseline comparison.
- JFR-enabled load task.
- Generated reader fallback tracking.
- Netty `ByteBuf` writer scaffold.
- JMH benchmark scaffold.

Next frontier:

- Use JFR allocation data to choose between DTO construction, string decoding,
  and output buffering optimizations.
- Generate `TryFastJsonReader` implementations from observed shapes instead of
  hand-writing prototype readers.
- Add a WebFlux codec that writes directly into pooled `ByteBuf` instances.
