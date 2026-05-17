# 성능 검증

[English](PERFORMANCE.md)

첫 번째 반복 가능한 성능 검증은 `realisticLoadTest`입니다.

```bash
./gradlew realisticLoadTest
```

기본 모델:

- worker thread 8개
- scenario별 worker당 12,000 iterations
- 479 byte checkout request
- 1,764 byte order summary response
- returned object와 byte array가 실제로 사용되도록 blackhole consumption 적용
- 최종 `byte[]` copy를 피하는 lower-allocation response path를 추정하기 위해
  reusable-buffer writer scenario 포함
- 등록된 `FastJsonBufferWriter`를 사용하는 실제 `HttpMessageConverter` 경로를
  측정하기 위해 Spring direct writer scenario 포함

실행 조건 조정:

```bash
./gradlew realisticLoadTest -PloadThreads=16 -PloadIterations=50000
```

JFR profile 캡처:

```bash
./gradlew jfrRealisticLoadTest
```

recording은 다음 위치에 생성됩니다.

```text
build/reports/json-fastlane/realistic-load.jfr
```

JMH 실행:

```bash
./gradlew jmh
./gradlew jmh -PjmhInclude=io.jsonfastlane.bench.JsonFastPathBenchmark.fastlaneWriteNettyByteBuf
```

실행 가능한 성능 task는 모두 `json-fastlane-benchmarks` 모듈에 있지만, Gradle
root에서 task 이름으로 바로 실행할 수 있습니다.

이 workspace의 최신 로컬 결과:

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

현재 해석:

- profiling converter는 plain Jackson보다 의도적으로 느립니다. body copy,
  timing 기록, JSON shape scan을 수행하기 때문입니다.
- generated-style checkout reader는 안정적인 request shape에서 Jackson보다
  빠릅니다. 최신 longer local run 기준 raw Jackson 대비 1.11배, Spring default
  conversion 대비 1.53배입니다.
- generated-style response writer도 nested response shape에서 Jackson보다
  빠릅니다. copied `byte[]`를 반환하는 경로 기준 최신 로컬 실행에서 throughput
  2.37배입니다.
- reusable-buffer response writer는 이 simulation에서 Jackson보다 훨씬 빠릅니다.
  최신 longer local run 기준 throughput 2.54배입니다. 다만 이것은 `writeValueAsBytes`와
  완전히 같은 조건의 대체가 아니라, 서버 integration이 최종 copy를 피하고
  reusable response buffer 또는 stream에 쓰는 방향으로 가야 한다는 신호입니다.
- Spring direct writer scenario는 서버-facing converter가 같은 generated writer
  registry를 사용해 중간 Jackson output buffer 없이 response body로 쓸 수 있음을
  검증합니다.
- dedicated generated writer converter는 Jackson 기반 profiling converter보다 얇고,
  최신 longer local run에서 Spring default write throughput 대비 1.22배에 도달했습니다.

최근 JVM 지향 최적화:

- generated writer registry가 class-to-writer lookup을 cache하고 per-write
  `Optional` allocation을 피합니다.
- `Utf8JsonBuffer.writeString`에 ASCII no-escape fast path를 추가했습니다.
  일반적인 서비스 payload에 잘 맞는 경로입니다.
- control-character unicode escape를 `String.format` 없이 직접 씁니다.
- generated reader cursor가 static JSON fragment를 cursor 이동 전에 비교하도록
  정리되어 bounds check elimination에 더 유리한 형태가 되었습니다.
- `FallbackJsonReader`가 uncommon payload shape에 대해 fast-path/fallback read를
  추적합니다.
- `FallbackAwareJsonReader`는 `TryFastJsonReader.tryRead`를 사용해 exception 기반
  fallback을 피합니다. uncommon shape가 정상적으로 들어올 수 있는 경우 훨씬 저렴합니다.
- `NettyJsonBuffer`와 `FastJsonByteBufWriter`가 Netty/WebFlux 스타일 integration을
  위한 첫 pooled-buffer 경로를 제공합니다.
- JMH benchmark는 `src/jmh/java` 아래에 있습니다.

shuffled request shape 기준 최신 fallback-path 비교:

```text
fastlane-fallback-read-shuffled          904318 ops/s
fastlane-aware-fallback-read-shuffled   2039685 ops/s
```

두 scenario 모두 shuffled shape에서 100% fallback합니다. 차이는 fallback-aware
reader가 정상 fallback 경로에서 exception을 던지지 않고 mismatch를 감지한다는 점입니다.
- 이 테스트는 JMH나 production profiling의 대체물이 아니라 load simulation입니다.
  실행마다 정확한 숫자는 달라질 수 있습니다. codec 아이디어가 발전하는 동안
  프로젝트를 현실적인 숫자로 붙잡아두기 위한 장치입니다.

완료한 성능 작업:

- realistic load simulation
- Spring default baseline 비교
- JFR-enabled load task
- generated reader fallback tracking
- Netty `ByteBuf` writer scaffold
- JMH benchmark scaffold

다음 frontier:

- JFR allocation data를 바탕으로 DTO construction, string decoding, output buffering
  중 다음 최적화 지점 선택
- 관측된 shape에서 `TryFastJsonReader` 구현을 generated code로 생성
- pooled `ByteBuf`에 직접 쓰는 WebFlux codec 추가
