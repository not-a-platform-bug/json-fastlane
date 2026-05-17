# Roadmap

[한국어](ROADMAP.ko.md)

## Project Structure

- Split core, Spring, Netty, and benchmark code into separate Gradle modules.
  Done.

## 0.1 Core Shape Profiler

- Capture endpoint-level payload size, root kind, field order, and field value kinds.
- Keep the profiler dependency-free.
- Provide generated-style writer examples to make the target runtime shape concrete.

## 0.2 Spring Profiling Adapter

- Add a Spring MVC adapter around request and response body conversion. Done in prototype form.
- Record parse and write durations by endpoint and DTO type. Done in prototype form.
- Route registered generated writers directly to the response body. Done in prototype form.
- Export summaries through logs first, then Micrometer and JFR.

## 0.3 Generated Writers

- Generate DTO-specific writers for Java records.
- Add Kotlin data class support after the Java path is stable.
- Keep static field names pre-encoded as UTF-8 bytes.
- Support reusable buffer writers that avoid the final `byte[]` copy. Done in prototype form.

## 0.4 Generated Readers

- Add endpoint-specific fast readers for stable object shapes. Done in prototype form.
- Preserve fallback behavior for unknown fields and uncommon shapes.
- Track fallback rates so users can tell when a fast path is too narrow. Done in prototype form.
- Avoid exception-driven fallback for expected shape drift. Done in prototype form.

## 0.5 Benchmarks

- Add realistic in-process load simulation against Jackson databind. Done in prototype form.
- Add JFR-enabled realistic load task for allocation and CPU profiling. Done in prototype form.
- Add JMH benchmarks against Jackson databind. Done in scaffold form.
- Add Netty/WebFlux pooled-buffer writer path. Done in scaffold form.
- Benchmark small DTOs, medium nested DTOs, and large arrays separately.
- Report throughput, p99 latency, allocations, and fallback rate.
