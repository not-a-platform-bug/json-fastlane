# json-fastlane

[English](README.md)

`json-fastlane`은 JVM 서버를 위한 profile-guided JSON 직렬화 실험입니다.

처음부터 Jackson을 대체하려는 프로젝트가 아닙니다. 첫 목표는 실제 API
payload shape를 관측하고, JSON 비용이 큰 endpoint를 찾고, hot DTO를 안전한
fallback이 있는 generated fast-path codec으로 보내는 것입니다.

## 현재 프로토타입

- endpoint별 top-level JSON field shape를 기록합니다.
- field order, field value kind, payload size, sample count를 추적합니다.
- reflection 없는 generated-style UTF-8 reader/writer 예제를 포함합니다.
- response serialization allocation을 줄이기 위한 reusable UTF-8 buffer writer 경로를 포함합니다.
- Spring MVC Jackson profiling converter 프로토타입을 포함합니다.

## 모듈

- `json-fastlane-core`: 가벼운 core profiler, reader/writer contract,
  fallback tracking, UTF-8 buffer, generated-style 예제
- `json-fastlane-spring`: Spring MVC converter, generated writer registry,
  Jackson fallback/profiling integration
- `json-fastlane-netty`: pooled-buffer WebFlux/Reactor Netty 스타일 integration을
  위한 Netty `ByteBuf` writer scaffold
- `json-fastlane-benchmarks`: smoke check, realistic load simulation, JFR 실행,
  JMH benchmark

## 실행

```bash
./gradlew :json-fastlane-core:run
./gradlew check
./gradlew realisticLoadTest
./gradlew realisticLoadTest -PloadThreads=16 -PloadIterations=50000
./gradlew jfrRealisticLoadTest
./gradlew jmh -PjmhWarmups=1 -PjmhIterations=1 -PjmhForks=1
```

## Spring MVC adapter

프로토타입은 profiling Jackson converter를 제공합니다.

```java
JsonConversionProfiler profiler = new JsonConversionProfiler();
FastJsonWriterRegistry writerRegistry = new FastJsonWriterRegistry();
ProfilingJackson2HttpMessageConverter converter =
    new ProfilingJackson2HttpMessageConverter(objectMapper, profiler, writerRegistry);

writerRegistry.register(OrderSummaryResponse.class, new OrderSummaryResponseWriter());
```

response type에 등록된 `FastJsonBufferWriter`가 있으면 converter는 다음 경로로
바로 씁니다.

```text
DTO -> reusable Utf8JsonBuffer -> response OutputStream
```

등록된 writer가 없으면 Jackson으로 fallback합니다. Spring MVC 앱에서는 Web MVC
설정에서 첫 Jackson converter를 교체할 수 있습니다.

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

interceptor 또는 filter는 request 처리 범위에서 현재 endpoint를 설정해야 합니다.

```java
try (JsonFastlaneSpring.EndpointScope ignored =
         JsonFastlaneSpring.withCurrentEndpoint("/orders")) {
    // Spring request handling continues here.
}
```

converter가 기록하는 정보:

- read/write 방향
- endpoint
- Java type
- 평균 payload bytes
- 평균 conversion time
- core profiler가 관측한 JSON field shape

## 현실적인 부하 시뮬레이션

`realisticLoadTest`는 두 API shape를 in-process load model로 실행합니다.

- `POST /checkout`: 장바구니 item, 주소, 쿠폰, trace id를 포함한 nested request JSON
- `GET /orders/{id}/summary`: 12개 order line, 배송, 결제, timeline, memo를 포함한 nested response JSON

비교 대상:

- Jackson deserialization
- profiling Spring converter deserialization
- generated-style fast reader deserialization
- Jackson serialization
- profiling Spring converter serialization
- generated-style fast writer serialization
- generated-style reusable-buffer writer serialization
- Spring converter direct generated writer serialization

task는 throughput, p50/p95/p99 latency, converter profiling snapshot,
관측된 JSON field shape를 출력합니다. 부하 시작 전에는 generated reader 결과와
generated writer JSON이 Jackson 결과와 동등한지도 검증합니다.

## 방향

1. 관측된 endpoint shape에서 DTO-specific reader/writer를 생성합니다.
2. JSON 비용 리포트를 logs, Micrometer, JFR event로 내보냅니다.
3. generated reader의 fallback rate를 추적합니다.
4. unknown shape와 호환성을 위해 Jackson fallback을 유지합니다.
