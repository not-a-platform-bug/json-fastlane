# Design Notes

[한국어](DESIGN.ko.md)

`json-fastlane` is built around three loops.

## Module Boundaries

The project is split by optional runtime dependency:

- `json-fastlane-core` owns the contracts and fast-path primitives. It should
  stay independent from Spring, Jackson, and Netty.
- `json-fastlane-spring` adapts the core contracts to Spring MVC and keeps
  Jackson as fallback.
- `json-fastlane-netty` adapts the writer path to pooled `ByteBuf` output.
- `json-fastlane-benchmarks` owns executable experiments so benchmark-only
  dependencies do not leak into production artifacts.

## Observe

The profiler records the shape of real JSON payloads. The initial prototype
keeps this intentionally small: endpoint, payload size, root kind, top-level
field order, and field value kinds.

This is enough to answer the first useful question:

> Are our payloads stable enough to deserve a generated fast path?

## Specialize

Once a DTO and endpoint are hot, generated code can assume the common shape
first. For example, a generated reader can compare expected UTF-8 field names
directly and a generated writer can emit static field names without reflection.

The generated path should optimize for the common payload, not for every JSON
document in the world.

The JVM-friendly form matters as much as the algorithm:

- Keep generated call sites monomorphic where possible.
- Reuse buffers so serialization output does not escape as a fresh `byte[]`
  unless the caller explicitly asks for one.
- Avoid per-field metadata lookup, reflection, and temporary `String`
  allocation.
- Shape byte-array loops so HotSpot can inline and remove redundant bounds
  checks.

## Fallback

The project should keep compatibility boring. If the observed shape does not
match, or if a feature is not implemented, the integration should fall back to
the application JSON stack.

The fast path is an optimization. Correctness belongs to the fallback until a
generated codec proves it can cover the case safely.

Fallback should not require exceptions for expected shape drift. Generated
readers should prefer this shape:

```text
TryFastJsonReader.tryRead(bytes) -> DTO or null
FallbackAwareJsonReader -> fallback when null
```

Exceptions remain useful for malformed JSON, but uncommon valid JSON should be
a normal branch.
