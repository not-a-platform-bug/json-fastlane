# 로드맵

[English](ROADMAP.md)

## 프로젝트 구조

- core, Spring, Netty, benchmark code를 별도 Gradle module로 분리합니다. 완료.

## 0.1 Core Shape Profiler

- endpoint-level payload size, root kind, field order, field value kind를 수집합니다.
- profiler core는 dependency-free로 유지합니다.
- generated-style writer 예제를 제공해서 목표 runtime 형태를 구체화합니다.

## 0.2 Spring Profiling Adapter

- request/response body conversion 주변에 Spring MVC adapter를 추가합니다. 프로토타입 완료.
- endpoint와 DTO type별 parse/write duration을 기록합니다. 프로토타입 완료.
- 등록된 generated writer를 response body로 직접 보냅니다. 프로토타입 완료.
- 요약 리포트를 log로 먼저 내보내고, 이후 Micrometer와 JFR로 확장합니다.

## 0.3 Generated Writers

- Java record용 DTO-specific writer를 생성합니다.
- Java 경로가 안정화된 뒤 Kotlin data class를 지원합니다.
- static field name은 UTF-8 bytes로 미리 encoding해둡니다.
- 최종 `byte[]` copy를 피하는 reusable buffer writer를 지원합니다. 프로토타입 완료.

## 0.4 Generated Readers

- stable object shape를 위한 endpoint-specific fast reader를 추가합니다. 프로토타입 완료.
- unknown field와 uncommon shape에 대해 fallback 동작을 보존합니다.
- fast path가 너무 좁은지 알 수 있도록 fallback rate를 추적합니다. 프로토타입 완료.
- 예상 가능한 shape drift에서 exception 기반 fallback을 피합니다. 프로토타입 완료.

## 0.5 Benchmarks

- Jackson databind와 비교하는 realistic in-process load simulation을 추가합니다. 프로토타입 완료.
- allocation과 CPU profiling을 위한 JFR-enabled realistic load task를 추가합니다. 프로토타입 완료.
- Jackson databind와 비교하는 JMH benchmark를 추가합니다. scaffold 완료.
- Netty/WebFlux pooled-buffer writer path를 추가합니다. scaffold 완료.
- small DTO, medium nested DTO, large array를 분리해 benchmark합니다.
- throughput, p99 latency, allocation, fallback rate를 리포트합니다.
