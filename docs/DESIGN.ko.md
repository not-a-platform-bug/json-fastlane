# 설계 노트

[English](DESIGN.md)

`json-fastlane`은 세 가지 loop를 중심으로 설계합니다.

## 모듈 경계

프로젝트는 optional runtime dependency 기준으로 나뉩니다.

- `json-fastlane-core`는 contract와 fast-path primitive를 소유합니다. Spring,
  Jackson, Netty와 독립적으로 유지해야 합니다.
- `json-fastlane-spring`은 core contract를 Spring MVC에 연결하고 Jackson fallback을
  유지합니다.
- `json-fastlane-netty`는 writer path를 pooled `ByteBuf` output에 연결합니다.
- `json-fastlane-benchmarks`는 실행 가능한 실험을 소유해서 benchmark-only
  dependency가 production artifact로 새지 않게 합니다.

## Observe

profiler는 실제 JSON payload의 shape를 기록합니다. 초기 프로토타입은 의도적으로
작게 유지합니다. endpoint, payload size, root kind, top-level field order,
field value kind를 기록합니다.

이 정보만으로도 첫 번째로 중요한 질문에 답할 수 있습니다.

> 우리 payload가 generated fast path를 만들 만큼 안정적인가?

## Specialize

DTO와 endpoint가 hot path로 확인되면 generated code는 common shape를 먼저
가정할 수 있습니다. generated reader는 예상 UTF-8 field name을 직접 비교하고,
generated writer는 reflection 없이 static field name을 바로 출력할 수 있습니다.

generated path는 세상의 모든 JSON 문서가 아니라, 실제 서비스에서 자주 들어오는
payload에 최적화되어야 합니다.

JVM 친화적인 코드 형태도 알고리즘만큼 중요합니다.

- 가능하면 generated call site를 monomorphic하게 유지합니다.
- caller가 명시적으로 `byte[]`를 요구하지 않는 한 serialization output이 fresh
  `byte[]`로 escape하지 않도록 buffer를 재사용합니다.
- per-field metadata lookup, reflection, 임시 `String` allocation을 피합니다.
- HotSpot이 inline과 redundant bounds check 제거를 하기 쉽도록 byte-array loop를
  단순한 형태로 유지합니다.

## Fallback

호환성은 지루할 만큼 안전해야 합니다. 관측된 shape와 맞지 않거나 아직 구현되지
않은 feature를 만나면 integration은 애플리케이션의 기존 JSON stack으로 fallback해야
합니다.

fast path는 최적화입니다. generated codec이 안전하게 처리할 수 있음을 증명하기
전까지 correctness는 fallback의 책임입니다.

예상 가능한 shape drift에 대해 fallback이 exception을 요구해서는 안 됩니다.
generated reader는 다음 형태를 우선해야 합니다.

```text
TryFastJsonReader.tryRead(bytes) -> DTO or null
FallbackAwareJsonReader -> null이면 fallback
```

malformed JSON에는 exception이 유용하지만, 유효하지만 드문 JSON shape는 정상 분기로
처리되어야 합니다.
