package io.jsonfastlane;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonfastlane.spring.ConversionProfileSnapshot;
import io.jsonfastlane.spring.FastJsonHttpMessageConverter;
import io.jsonfastlane.spring.FastJsonWriterRegistry;
import io.jsonfastlane.spring.JsonConversionProfiler;
import io.jsonfastlane.spring.JsonFastlaneSpring;
import io.jsonfastlane.spring.ProfilingJackson2HttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RealisticLoadSimulation {
    private static final String CHECKOUT_ENDPOINT = "/checkout";
    private static final String SUMMARY_ENDPOINT = "/orders/{id}/summary";
    private static volatile long blackhole;

    private RealisticLoadSimulation() {
    }

    public static void main(String[] args) throws Exception {
        int threads = integerProperty("jsonfastlane.load.threads", 8);
        int iterationsPerThread = integerProperty("jsonfastlane.load.iterations", 12_000);

        ObjectMapper objectMapper = new ObjectMapper();
        MappingJackson2HttpMessageConverter springDefaultConverter =
            new MappingJackson2HttpMessageConverter(objectMapper);
        JsonConversionProfiler profiler = new JsonConversionProfiler();
        ProfilingJackson2HttpMessageConverter converter =
            new ProfilingJackson2HttpMessageConverter(objectMapper, profiler);
        FastJsonWriterRegistry writerRegistry = new FastJsonWriterRegistry();

        CheckoutRequest request = checkoutRequest();
        OrderSummaryResponse response = orderSummaryResponse();
        CheckoutRequestReader fastReader = new CheckoutRequestReader();
        CheckoutRequestTryReader tryReader = new CheckoutRequestTryReader();
        FallbackJsonReader<CheckoutRequest> fallbackReader = new FallbackJsonReader<>(
            fastReader,
            bytes -> {
                try {
                    return objectMapper.readValue(bytes, CheckoutRequest.class);
                } catch (Exception exception) {
                    throw new IllegalArgumentException(exception);
                }
            }
        );
        FallbackAwareJsonReader<CheckoutRequest> fallbackAwareReader = new FallbackAwareJsonReader<>(
            tryReader,
            bytes -> {
                try {
                    return objectMapper.readValue(bytes, CheckoutRequest.class);
                } catch (Exception exception) {
                    throw new IllegalArgumentException(exception);
                }
            }
        );
        OrderSummaryResponseWriter fastWriter = new OrderSummaryResponseWriter();
        writerRegistry.register(OrderSummaryResponse.class, fastWriter);
        ThreadLocal<Utf8JsonBuffer> reusableWriterBuffer =
            ThreadLocal.withInitial(() -> new Utf8JsonBuffer(jacksonResponseBytesCapacityHint()));

        byte[] requestBytes = objectMapper.writeValueAsBytes(request);
        byte[] shuffledRequestBytes = shuffledCheckoutRequestJson().getBytes(StandardCharsets.UTF_8);
        byte[] jacksonResponseBytes = objectMapper.writeValueAsBytes(response);
        byte[] fastResponseBytes = fastWriter.write(response);
        SimpleOutputMessage directOutput = new SimpleOutputMessage();
        new ProfilingJackson2HttpMessageConverter(
            objectMapper,
            new JsonConversionProfiler(),
            writerRegistry
        ).write(response, MediaType.APPLICATION_JSON, directOutput);
        CheckoutRequest fastRequest = fastReader.read(requestBytes);
        if (!request.equals(fastRequest)) {
            throw new AssertionError("Generated reader did not produce an equivalent request.");
        }
        assertSameJson(objectMapper, jacksonResponseBytes, fastResponseBytes);
        assertSameJson(objectMapper, jacksonResponseBytes, directOutput.bytes());

        JsonConversionProfiler directWriterProfiler = new JsonConversionProfiler();
        ProfilingJackson2HttpMessageConverter directWriterConverter =
            new ProfilingJackson2HttpMessageConverter(objectMapper, directWriterProfiler, writerRegistry);
        JsonConversionProfiler dedicatedWriterProfiler = new JsonConversionProfiler();
        FastJsonHttpMessageConverter dedicatedWriterConverter =
            new FastJsonHttpMessageConverter(dedicatedWriterProfiler, writerRegistry);

        System.out.println("json-fastlane realistic load simulation");
        System.out.println("threads=" + threads + " iterationsPerThread=" + iterationsPerThread);
        System.out.println("checkoutRequestBytes=" + requestBytes.length
            + " orderSummaryResponseBytes=" + jacksonResponseBytes.length);
        System.out.println();

        List<Scenario> scenarios = List.of(
            new Scenario("jackson-read-checkout", () ->
                objectMapper.readValue(requestBytes, CheckoutRequest.class)),
            new Scenario("spring-default-read-checkout", () ->
                springDefaultConverter.read(CheckoutRequest.class, new SimpleInputMessage(requestBytes))),
            new Scenario("profiling-converter-read-checkout", () -> {
                try (JsonFastlaneSpring.EndpointScope ignored =
                         JsonFastlaneSpring.withCurrentEndpoint(CHECKOUT_ENDPOINT)) {
                    return converter.read(CheckoutRequest.class, new SimpleInputMessage(requestBytes));
                }
            }),
            new Scenario("fastlane-generated-read-checkout", () ->
                fastReader.read(requestBytes)),
            new Scenario("fastlane-fallback-read-shuffled", () ->
                fallbackReader.read(shuffledRequestBytes)),
            new Scenario("fastlane-aware-fallback-read-shuffled", () ->
                fallbackAwareReader.read(shuffledRequestBytes)),
            new Scenario("jackson-write-summary", () ->
                objectMapper.writeValueAsBytes(response)),
            new Scenario("spring-default-write-summary", () -> {
                SimpleOutputMessage output = new SimpleOutputMessage();
                springDefaultConverter.write(response, MediaType.APPLICATION_JSON, output);
                return output;
            }),
            new Scenario("profiling-converter-write-summary", () -> {
                try (JsonFastlaneSpring.EndpointScope ignored =
                         JsonFastlaneSpring.withCurrentEndpoint(SUMMARY_ENDPOINT)) {
                    SimpleOutputMessage output = new SimpleOutputMessage();
                    converter.write(response, MediaType.APPLICATION_JSON, output);
                    return output.bytes();
                }
            }),
            new Scenario("fastlane-generated-write-summary", () ->
                fastWriter.write(response)),
            new Scenario("fastlane-reused-buffer-write-summary", () -> {
                Utf8JsonBuffer out = reusableWriterBuffer.get().reset();
                fastWriter.write(response, out);
                return out;
            }),
            new Scenario("fastlane-spring-direct-write-summary", () -> {
                try (JsonFastlaneSpring.EndpointScope ignored =
                         JsonFastlaneSpring.withCurrentEndpoint(SUMMARY_ENDPOINT)) {
                    SimpleOutputMessage output = new SimpleOutputMessage();
                    directWriterConverter.write(response, MediaType.APPLICATION_JSON, output);
                    return output;
                }
            }),
            new Scenario("fastlane-spring-direct-sink-summary", () -> {
                try (JsonFastlaneSpring.EndpointScope ignored =
                         JsonFastlaneSpring.withCurrentEndpoint(SUMMARY_ENDPOINT)) {
                    SinkOutputMessage output = new SinkOutputMessage();
                    directWriterConverter.write(response, MediaType.APPLICATION_JSON, output);
                    return output;
                }
            }),
            new Scenario("fastlane-dedicated-sink-summary", () -> {
                try (JsonFastlaneSpring.EndpointScope ignored =
                         JsonFastlaneSpring.withCurrentEndpoint(SUMMARY_ENDPOINT)) {
                    SinkOutputMessage output = new SinkOutputMessage();
                    dedicatedWriterConverter.write(response, MediaType.APPLICATION_JSON, output);
                    return output;
                }
            })
        );

        for (Scenario scenario : scenarios) {
            warmUp(scenario);
        }

        List<ScenarioResult> results = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            results.add(runScenario(scenario, threads, iterationsPerThread));
        }

        printResults(results);
        printFallbackSnapshot("exception fallback reader", fallbackReader.snapshot());
        printFallbackSnapshot("fallback-aware reader", fallbackAwareReader.snapshot());
        printProfilerSnapshots(profiler);
        printProfilerSnapshots("Spring direct writer profiler snapshots", directWriterProfiler);
        printProfilerSnapshots("Dedicated fast writer profiler snapshots", dedicatedWriterProfiler);
        printShapeSnapshots(profiler);
    }

    private static void warmUp(Scenario scenario) throws Exception {
        for (int i = 0; i < 2_000; i++) {
            consume(scenario.operation().run());
        }
    }

    private static ScenarioResult runScenario(Scenario scenario, int threads, int iterationsPerThread)
        throws Exception {
        int totalOperations = threads * iterationsPerThread;
        long[] latencies = new long[totalOperations];
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int worker = 0; worker < threads; worker++) {
            int offset = worker * iterationsPerThread;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        long begin = System.nanoTime();
                        Object result = scenario.operation().run();
                        latencies[offset + i] = System.nanoTime() - begin;
                        consume(result);
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }

        long started = System.nanoTime();
        start.countDown();
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow();
            throw new IllegalStateException("Scenario timed out: " + scenario.name());
        }
        long elapsed = System.nanoTime() - started;

        Arrays.sort(latencies);
        return new ScenarioResult(
            scenario.name(),
            totalOperations,
            elapsed,
            percentile(latencies, 0.50),
            percentile(latencies, 0.95),
            percentile(latencies, 0.99)
        );
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static void printResults(List<ScenarioResult> results) {
        System.out.printf("%-38s %12s %12s %12s %12s%n",
            "scenario", "ops/s", "p50 us", "p95 us", "p99 us");
        for (ScenarioResult result : results) {
            System.out.printf(Locale.ROOT, "%-38s %12.0f %12.2f %12.2f %12.2f%n",
                result.name(),
                result.operationsPerSecond(),
                result.p50Nanos() / 1_000.0,
                result.p95Nanos() / 1_000.0,
                result.p99Nanos() / 1_000.0);
        }
        System.out.println();

        ScenarioResult jacksonWrite = find(results, "jackson-write-summary");
        ScenarioResult springDefaultWrite = find(results, "spring-default-write-summary");
        ScenarioResult fastWrite = find(results, "fastlane-generated-write-summary");
        ScenarioResult reusedFastWrite = find(results, "fastlane-reused-buffer-write-summary");
        ScenarioResult springDirectWrite = find(results, "fastlane-spring-direct-write-summary");
        ScenarioResult springDirectSink = find(results, "fastlane-spring-direct-sink-summary");
        ScenarioResult dedicatedSink = find(results, "fastlane-dedicated-sink-summary");
        ScenarioResult jacksonRead = find(results, "jackson-read-checkout");
        ScenarioResult springDefaultRead = find(results, "spring-default-read-checkout");
        ScenarioResult fastRead = find(results, "fastlane-generated-read-checkout");
        System.out.printf(Locale.ROOT, "generated reader throughput ratio vs Jackson: %.2fx%n",
            fastRead.operationsPerSecond() / jacksonRead.operationsPerSecond());
        System.out.printf(Locale.ROOT, "generated reader throughput ratio vs Spring default: %.2fx%n",
            fastRead.operationsPerSecond() / springDefaultRead.operationsPerSecond());
        System.out.printf(Locale.ROOT, "generated writer throughput ratio vs Jackson: %.2fx%n",
            fastWrite.operationsPerSecond() / jacksonWrite.operationsPerSecond());
        System.out.printf(Locale.ROOT, "dedicated converter sink throughput ratio vs Spring default: %.2fx%n",
            dedicatedSink.operationsPerSecond() / springDefaultWrite.operationsPerSecond());
        System.out.printf(Locale.ROOT, "reused-buffer writer throughput ratio vs Jackson: %.2fx%n",
            reusedFastWrite.operationsPerSecond() / jacksonWrite.operationsPerSecond());
        System.out.printf(Locale.ROOT, "Spring direct writer throughput ratio vs Jackson: %.2fx%n",
            springDirectWrite.operationsPerSecond() / jacksonWrite.operationsPerSecond());
        System.out.printf(Locale.ROOT, "Spring direct sink throughput ratio vs Jackson: %.2fx%n",
            springDirectSink.operationsPerSecond() / jacksonWrite.operationsPerSecond());
        System.out.printf(Locale.ROOT, "dedicated converter sink throughput ratio vs Jackson: %.2fx%n",
            dedicatedSink.operationsPerSecond() / jacksonWrite.operationsPerSecond());
        System.out.println("blackhole=" + blackhole);
        System.out.println();
    }

    private static void printFallbackSnapshot(String label, ReaderFallbackSnapshot snapshot) {
        System.out.printf(Locale.ROOT, "%s fast=%d fallback=%d fallbackRate=%.2f%%%n",
            label,
            snapshot.fastReads(),
            snapshot.fallbackReads(),
            snapshot.fallbackRate() * 100.0);
    }

    private static void consume(Object value) {
        if (value instanceof byte[] bytes) {
            blackhole ^= bytes.length;
            blackhole ^= bytes[0];
            blackhole ^= bytes[bytes.length - 1];
        } else if (value instanceof Utf8JsonBuffer buffer) {
            blackhole ^= buffer.size();
            blackhole ^= buffer.firstByte();
            blackhole ^= buffer.lastByte();
        } else if (value instanceof SimpleOutputMessage output) {
            blackhole ^= output.size();
            blackhole ^= output.firstByte();
            blackhole ^= output.lastByte();
        } else if (value instanceof SinkOutputMessage output) {
            blackhole ^= output.size();
            blackhole ^= output.firstByte();
            blackhole ^= output.lastByte();
        } else if (value instanceof CheckoutRequest request) {
            blackhole ^= request.userId();
            blackhole ^= request.items().size();
        } else {
            blackhole ^= value == null ? 0 : value.hashCode();
        }
    }

    private static ScenarioResult find(List<ScenarioResult> results, String name) {
        return results.stream()
            .filter(result -> result.name().equals(name))
            .findFirst()
            .orElseThrow();
    }

    private static void printProfilerSnapshots(JsonConversionProfiler profiler) {
        printProfilerSnapshots("Spring converter profiler snapshots", profiler);
    }

    private static void printProfilerSnapshots(String title, JsonConversionProfiler profiler) {
        System.out.println(title);
        for (ConversionProfileSnapshot snapshot : profiler.conversionSnapshots()) {
            System.out.printf(Locale.ROOT, "%s %s type=%s samples=%d avgBytes=%d avgMicros=%.2f%n",
                snapshot.endpoint(),
                snapshot.direction(),
                simpleName(snapshot.javaType()),
                snapshot.samples(),
                snapshot.averagePayloadBytes(),
                snapshot.averageNanos() / 1_000.0);
        }
        System.out.println();
    }

    private static void printShapeSnapshots(JsonConversionProfiler profiler) {
        System.out.println("Observed JSON shapes");
        for (EndpointProfileSnapshot snapshot : profiler.shapeProfiler().snapshots()) {
            System.out.printf("%s samples=%d avgBytes=%d%n",
                snapshot.endpoint(),
                snapshot.samples(),
                snapshot.averagePayloadBytes());
            snapshot.fieldOrders().stream().limit(1).forEach(order ->
                System.out.println("  commonOrder=" + order.signature() + " samples=" + order.samples()));
            snapshot.fields().stream()
                .sorted(Comparator.comparing(FieldProfileSnapshot::averagePosition))
                .forEach(field -> System.out.println("  field=" + field.name()
                    + " kinds=" + field.valueKinds()
                    + " avgPosition=" + String.format(Locale.ROOT, "%.1f", field.averagePosition())));
        }
    }

    private static String simpleName(String javaType) {
        int lastDot = javaType.lastIndexOf('.');
        return lastDot < 0 ? javaType : javaType.substring(lastDot + 1);
    }

    private static int integerProperty(String name, int fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private static int jacksonResponseBytesCapacityHint() {
        return 2048;
    }

    private static void assertSameJson(ObjectMapper objectMapper, byte[] left, byte[] right) throws Exception {
        JsonNode leftNode = objectMapper.readTree(left);
        JsonNode rightNode = objectMapper.readTree(right);
        if (!leftNode.equals(rightNode)) {
            throw new AssertionError("Generated JSON is not equivalent to Jackson JSON.\nJackson="
                + new String(left, StandardCharsets.UTF_8)
                + "\nFastlane=" + new String(right, StandardCharsets.UTF_8));
        }
    }

    private static CheckoutRequest checkoutRequest() {
        return new CheckoutRequest(
            492_001L,
            List.of(
                new CheckoutItem("SKU-COFFEE-1KG", 2, 18_900),
                new CheckoutItem("SKU-FILTER-100", 1, 8_400),
                new CheckoutItem("SKU-MUG-BLUE", 3, 12_000),
                new CheckoutItem("SKU-GIFT-WRAP", 1, 2_500)
            ),
            new ShippingAddress("KR", "Seoul", "Teheran-ro 427", "15F", "06159"),
            "SPRING-ORDER-10",
            false,
            "ios-8f07b9d6-7b7d-4bc4-a511-873f6c2d7c79"
        );
    }

    private static String shuffledCheckoutRequestJson() {
        return """
            {"clientTraceId":"ios-8f07b9d6-7b7d-4bc4-a511-873f6c2d7c79","gift":false,"couponCode":"SPRING-ORDER-10","shippingAddress":{"country":"KR","city":"Seoul","line1":"Teheran-ro 427","line2":"15F","postalCode":"06159"},"items":[{"sku":"SKU-COFFEE-1KG","quantity":2,"unitPriceCents":18900},{"sku":"SKU-FILTER-100","quantity":1,"unitPriceCents":8400},{"sku":"SKU-MUG-BLUE","quantity":3,"unitPriceCents":12000},{"sku":"SKU-GIFT-WRAP","quantity":1,"unitPriceCents":2500}],"userId":492001}
            """;
    }

    private static OrderSummaryResponse orderSummaryResponse() {
        List<OrderLine> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lines.add(new OrderLine(
                "SKU-" + (10_000 + i),
                "Catalog item " + i,
                (i % 4) + 1,
                5_900 + (i * 1_700L),
                i % 3 == 0
            ));
        }

        return new OrderSummaryResponse(
            88_104_220L,
            "PAID",
            492_001L,
            "kim@example.com",
            Instant.parse("2026-05-16T12:30:45Z").toString(),
            lines,
            new ShippingAddress("KR", "Seoul", "Teheran-ro 427", "15F", "06159"),
            new PaymentSummary("CARD", "KRW", 184_700, 14_000, 2_500, 173_200),
            List.of(
                new TimelineEvent("CREATED", "2026-05-16T12:30:45Z"),
                new TimelineEvent("PAID", "2026-05-16T12:30:47Z"),
                new TimelineEvent("PICKING", "2026-05-16T12:33:10Z")
            ),
            "Leave at the front desk if nobody answers."
        );
    }

    private record Scenario(String name, ThrowingOperation operation) {
    }

    private record ScenarioResult(
        String name,
        long operations,
        long elapsedNanos,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos
    ) {
        private double operationsPerSecond() {
            return operations / (elapsedNanos / 1_000_000_000.0);
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        Object run() throws Exception;
    }

    public record CheckoutRequest(
        long userId,
        List<CheckoutItem> items,
        ShippingAddress shippingAddress,
        String couponCode,
        boolean gift,
        String clientTraceId
    ) {
    }

    public record CheckoutItem(String sku, int quantity, long unitPriceCents) {
    }

    public record ShippingAddress(
        String country,
        String city,
        String line1,
        String line2,
        String postalCode
    ) {
    }

    public record OrderSummaryResponse(
        long orderId,
        String status,
        long userId,
        String email,
        String createdAt,
        List<OrderLine> lines,
        ShippingAddress shippingAddress,
        PaymentSummary payment,
        List<TimelineEvent> timeline,
        String deliveryMemo
    ) {
    }

    public record OrderLine(
        String sku,
        String name,
        int quantity,
        long unitPriceCents,
        boolean discounted
    ) {
    }

    public record PaymentSummary(
        String method,
        String currency,
        long subtotalCents,
        long discountCents,
        long shippingCents,
        long totalCents
    ) {
    }

    public record TimelineEvent(String status, String at) {
    }

    private static final class CheckoutRequestReader implements FastJsonReader<CheckoutRequest> {
        private static final byte[] USER_ID = ascii("{\"userId\":");
        private static final byte[] ITEMS = ascii(",\"items\":[");
        private static final byte[] SHIPPING_ADDRESS = ascii(",\"shippingAddress\":");
        private static final byte[] COUPON_CODE = ascii(",\"couponCode\":");
        private static final byte[] GIFT = ascii(",\"gift\":");
        private static final byte[] CLIENT_TRACE_ID = ascii(",\"clientTraceId\":");
        private static final byte[] SKU = ascii("{\"sku\":");
        private static final byte[] QUANTITY = ascii(",\"quantity\":");
        private static final byte[] UNIT_PRICE_CENTS = ascii(",\"unitPriceCents\":");
        private static final byte[] COUNTRY = ascii("{\"country\":");
        private static final byte[] CITY = ascii(",\"city\":");
        private static final byte[] LINE_1 = ascii(",\"line1\":");
        private static final byte[] LINE_2 = ascii(",\"line2\":");
        private static final byte[] POSTAL_CODE = ascii(",\"postalCode\":");

        @Override
        public CheckoutRequest read(byte[] utf8Json) {
            Cursor in = new Cursor(utf8Json);
            in.expect(USER_ID);
            long userId = in.readLong();
            in.expect(ITEMS);
            List<CheckoutItem> items = readItems(in);
            in.expect(SHIPPING_ADDRESS);
            ShippingAddress shippingAddress = readAddress(in);
            in.expect(COUPON_CODE);
            String couponCode = in.readNullableString();
            in.expect(GIFT);
            boolean gift = in.readBoolean();
            in.expect(CLIENT_TRACE_ID);
            String clientTraceId = in.readNullableString();
            in.expect('}');
            in.expectEnd();
            return new CheckoutRequest(userId, items, shippingAddress, couponCode, gift, clientTraceId);
        }

        private static List<CheckoutItem> readItems(Cursor in) {
            if (in.peek(']')) {
                in.expect(']');
                return List.of();
            }

            List<CheckoutItem> items = new ArrayList<>(4);
            while (true) {
                items.add(readItem(in));
                if (in.peek(',')) {
                    in.expect(',');
                    continue;
                }
                in.expect(']');
                return items;
            }
        }

        private static CheckoutItem readItem(Cursor in) {
            in.expect(SKU);
            String sku = in.readNullableString();
            in.expect(QUANTITY);
            int quantity = (int) in.readLong();
            in.expect(UNIT_PRICE_CENTS);
            long unitPriceCents = in.readLong();
            in.expect('}');
            return new CheckoutItem(sku, quantity, unitPriceCents);
        }

        private static ShippingAddress readAddress(Cursor in) {
            in.expect(COUNTRY);
            String country = in.readNullableString();
            in.expect(CITY);
            String city = in.readNullableString();
            in.expect(LINE_1);
            String line1 = in.readNullableString();
            in.expect(LINE_2);
            String line2 = in.readNullableString();
            in.expect(POSTAL_CODE);
            String postalCode = in.readNullableString();
            in.expect('}');
            return new ShippingAddress(country, city, line1, line2, postalCode);
        }

        private static byte[] ascii(String value) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final class CheckoutRequestTryReader implements TryFastJsonReader<CheckoutRequest> {
        private static final byte[] USER_ID = ascii("{\"userId\":");
        private static final byte[] ITEMS = ascii(",\"items\":[");
        private static final byte[] SHIPPING_ADDRESS = ascii(",\"shippingAddress\":");
        private static final byte[] COUPON_CODE = ascii(",\"couponCode\":");
        private static final byte[] GIFT = ascii(",\"gift\":");
        private static final byte[] CLIENT_TRACE_ID = ascii(",\"clientTraceId\":");
        private static final byte[] SKU = ascii("{\"sku\":");
        private static final byte[] QUANTITY = ascii(",\"quantity\":");
        private static final byte[] UNIT_PRICE_CENTS = ascii(",\"unitPriceCents\":");
        private static final byte[] COUNTRY = ascii("{\"country\":");
        private static final byte[] CITY = ascii(",\"city\":");
        private static final byte[] LINE_1 = ascii(",\"line1\":");
        private static final byte[] LINE_2 = ascii(",\"line2\":");
        private static final byte[] POSTAL_CODE = ascii(",\"postalCode\":");

        @Override
        public CheckoutRequest tryRead(byte[] utf8Json) {
            Cursor in = new Cursor(utf8Json);
            if (!in.tryExpect(USER_ID)) {
                return null;
            }
            long userId = in.readLong();
            if (!in.tryExpect(ITEMS)) {
                return null;
            }
            List<CheckoutItem> items = readItems(in);
            if (!in.tryExpect(SHIPPING_ADDRESS)) {
                return null;
            }
            ShippingAddress shippingAddress = readAddress(in);
            if (!in.tryExpect(COUPON_CODE)) {
                return null;
            }
            String couponCode = in.readNullableString();
            if (!in.tryExpect(GIFT)) {
                return null;
            }
            boolean gift = in.readBoolean();
            if (!in.tryExpect(CLIENT_TRACE_ID)) {
                return null;
            }
            String clientTraceId = in.readNullableString();
            if (!in.tryExpect('}') || !in.isAtEnd()) {
                return null;
            }
            return new CheckoutRequest(userId, items, shippingAddress, couponCode, gift, clientTraceId);
        }

        private static List<CheckoutItem> readItems(Cursor in) {
            if (in.peek(']')) {
                in.expect(']');
                return List.of();
            }

            List<CheckoutItem> items = new ArrayList<>(4);
            while (true) {
                items.add(readItem(in));
                if (in.peek(',')) {
                    in.expect(',');
                    continue;
                }
                in.expect(']');
                return items;
            }
        }

        private static CheckoutItem readItem(Cursor in) {
            in.expect(SKU);
            String sku = in.readNullableString();
            in.expect(QUANTITY);
            int quantity = (int) in.readLong();
            in.expect(UNIT_PRICE_CENTS);
            long unitPriceCents = in.readLong();
            in.expect('}');
            return new CheckoutItem(sku, quantity, unitPriceCents);
        }

        private static ShippingAddress readAddress(Cursor in) {
            in.expect(COUNTRY);
            String country = in.readNullableString();
            in.expect(CITY);
            String city = in.readNullableString();
            in.expect(LINE_1);
            String line1 = in.readNullableString();
            in.expect(LINE_2);
            String line2 = in.readNullableString();
            in.expect(POSTAL_CODE);
            String postalCode = in.readNullableString();
            in.expect('}');
            return new ShippingAddress(country, city, line1, line2, postalCode);
        }

        private static byte[] ascii(String value) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int index;

        private Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        private void expect(byte[] expected) {
            int start = index;
            int end = start + expected.length;
            if (end > bytes.length) {
                throw error("Expected static JSON fragment");
            }
            for (int i = 0; i < expected.length; i++) {
                if (bytes[start + i] != expected[i]) {
                    throw error("Unexpected JSON fragment");
                }
            }
            index = end;
        }

        private boolean tryExpect(byte[] expected) {
            int start = index;
            int end = start + expected.length;
            if (end > bytes.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (bytes[start + i] != expected[i]) {
                    return false;
                }
            }
            index = end;
            return true;
        }

        private void expect(char expected) {
            if (index >= bytes.length || bytes[index++] != (byte) expected) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean tryExpect(char expected) {
            if (index >= bytes.length || bytes[index] != (byte) expected) {
                return false;
            }
            index++;
            return true;
        }

        private boolean peek(char expected) {
            return index < bytes.length && bytes[index] == (byte) expected;
        }

        private void expectEnd() {
            if (index != bytes.length) {
                throw error("Expected end of JSON");
            }
        }

        private boolean isAtEnd() {
            return index == bytes.length;
        }

        private long readLong() {
            boolean negative = false;
            if (peek('-')) {
                negative = true;
                index++;
            }

            long value = 0;
            int start = index;
            while (index < bytes.length) {
                byte current = bytes[index];
                if (current < '0' || current > '9') {
                    break;
                }
                value = (value * 10) + (current - '0');
                index++;
            }
            if (start == index) {
                throw error("Expected number");
            }
            return negative ? -value : value;
        }

        private boolean readBoolean() {
            if (matches("true")) {
                index += 4;
                return true;
            }
            if (matches("false")) {
                index += 5;
                return false;
            }
            throw error("Expected boolean");
        }

        private String readNullableString() {
            if (matches("null")) {
                index += 4;
                return null;
            }
            return readString();
        }

        private String readString() {
            expect('"');
            int start = index;
            boolean ascii = true;
            while (index < bytes.length) {
                byte current = bytes[index++];
                if (current == '"') {
                    return new String(
                        bytes,
                        start,
                        index - start - 1,
                        ascii ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8
                    );
                }
                if (current == '\\') {
                    return readEscapedString(start);
                }
                ascii &= current >= 0;
            }
            throw error("Unterminated string");
        }

        private String readEscapedString(int start) {
            StringBuilder builder = new StringBuilder();
            builder.append(new String(bytes, start, index - start - 1, StandardCharsets.UTF_8));
            while (index < bytes.length) {
                byte current = bytes[index++];
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    builder.append(readEscapedCharacter());
                } else {
                    int charStart = index - 1;
                    while (index < bytes.length && bytes[index] != '"' && bytes[index] != '\\') {
                        index++;
                    }
                    builder.append(new String(bytes, charStart, index - charStart, StandardCharsets.UTF_8));
                }
            }
            throw error("Unterminated string");
        }

        private char readEscapedCharacter() {
            if (index >= bytes.length) {
                throw error("Unterminated escape");
            }
            byte escaped = bytes[index++];
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicodeEscape();
                default -> throw error("Invalid escape");
            };
        }

        private char readUnicodeEscape() {
            if (index + 4 > bytes.length) {
                throw error("Invalid unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit((char) bytes[index++], 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private boolean matches(String expected) {
            if (index + expected.length() > bytes.length) {
                return false;
            }
            for (int i = 0; i < expected.length(); i++) {
                if (bytes[index + i] != (byte) expected.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }

    private static final class OrderSummaryResponseWriter
        implements FastJsonWriter<OrderSummaryResponse>, FastJsonBufferWriter<OrderSummaryResponse> {
        private static final byte[] ORDER_ID = ascii("{\"orderId\":");
        private static final byte[] STATUS = ascii(",\"status\":");
        private static final byte[] USER_ID = ascii(",\"userId\":");
        private static final byte[] EMAIL = ascii(",\"email\":");
        private static final byte[] CREATED_AT = ascii(",\"createdAt\":");
        private static final byte[] LINES = ascii(",\"lines\":[");
        private static final byte[] SHIPPING_ADDRESS = ascii(",\"shippingAddress\":");
        private static final byte[] PAYMENT = ascii(",\"payment\":");
        private static final byte[] TIMELINE = ascii(",\"timeline\":[");
        private static final byte[] DELIVERY_MEMO = ascii(",\"deliveryMemo\":");
        private static final byte[] SKU = ascii("{\"sku\":");
        private static final byte[] NAME = ascii(",\"name\":");
        private static final byte[] QUANTITY = ascii(",\"quantity\":");
        private static final byte[] UNIT_PRICE_CENTS = ascii(",\"unitPriceCents\":");
        private static final byte[] DISCOUNTED = ascii(",\"discounted\":");
        private static final byte[] COUNTRY = ascii("{\"country\":");
        private static final byte[] CITY = ascii(",\"city\":");
        private static final byte[] LINE_1 = ascii(",\"line1\":");
        private static final byte[] LINE_2 = ascii(",\"line2\":");
        private static final byte[] POSTAL_CODE = ascii(",\"postalCode\":");
        private static final byte[] METHOD = ascii("{\"method\":");
        private static final byte[] CURRENCY = ascii(",\"currency\":");
        private static final byte[] SUBTOTAL_CENTS = ascii(",\"subtotalCents\":");
        private static final byte[] DISCOUNT_CENTS = ascii(",\"discountCents\":");
        private static final byte[] SHIPPING_CENTS = ascii(",\"shippingCents\":");
        private static final byte[] TOTAL_CENTS = ascii(",\"totalCents\":");
        private static final byte[] TIMELINE_STATUS = ascii("{\"status\":");
        private static final byte[] AT = ascii(",\"at\":");

        @Override
        public byte[] write(OrderSummaryResponse value) {
            Utf8JsonBuffer out = new Utf8JsonBuffer(2048);
            write(value, out);
            return out.toByteArray();
        }

        @Override
        public void write(OrderSummaryResponse value, Utf8JsonBuffer out) {
            out.writeRaw(ORDER_ID).writeLong(value.orderId());
            out.writeRaw(STATUS).writeString(value.status());
            out.writeRaw(USER_ID).writeLong(value.userId());
            out.writeRaw(EMAIL).writeString(value.email());
            out.writeRaw(CREATED_AT).writeString(value.createdAt());
            out.writeRaw(LINES);
            for (int i = 0; i < value.lines().size(); i++) {
                if (i > 0) {
                    out.writeByte(',');
                }
                writeLine(out, value.lines().get(i));
            }
            out.writeByte(']');
            out.writeRaw(SHIPPING_ADDRESS);
            writeAddress(out, value.shippingAddress());
            out.writeRaw(PAYMENT);
            writePayment(out, value.payment());
            out.writeRaw(TIMELINE);
            for (int i = 0; i < value.timeline().size(); i++) {
                if (i > 0) {
                    out.writeByte(',');
                }
                writeTimeline(out, value.timeline().get(i));
            }
            out.writeByte(']');
            out.writeRaw(DELIVERY_MEMO).writeString(value.deliveryMemo());
            out.writeByte('}');
        }

        private static void writeLine(Utf8JsonBuffer out, OrderLine line) {
            out.writeRaw(SKU).writeString(line.sku());
            out.writeRaw(NAME).writeString(line.name());
            out.writeRaw(QUANTITY).writeInt(line.quantity());
            out.writeRaw(UNIT_PRICE_CENTS).writeLong(line.unitPriceCents());
            out.writeRaw(DISCOUNTED).writeBoolean(line.discounted());
            out.writeByte('}');
        }

        private static void writeAddress(Utf8JsonBuffer out, ShippingAddress address) {
            out.writeRaw(COUNTRY).writeString(address.country());
            out.writeRaw(CITY).writeString(address.city());
            out.writeRaw(LINE_1).writeString(address.line1());
            out.writeRaw(LINE_2).writeString(address.line2());
            out.writeRaw(POSTAL_CODE).writeString(address.postalCode());
            out.writeByte('}');
        }

        private static void writePayment(Utf8JsonBuffer out, PaymentSummary payment) {
            out.writeRaw(METHOD).writeString(payment.method());
            out.writeRaw(CURRENCY).writeString(payment.currency());
            out.writeRaw(SUBTOTAL_CENTS).writeLong(payment.subtotalCents());
            out.writeRaw(DISCOUNT_CENTS).writeLong(payment.discountCents());
            out.writeRaw(SHIPPING_CENTS).writeLong(payment.shippingCents());
            out.writeRaw(TOTAL_CENTS).writeLong(payment.totalCents());
            out.writeByte('}');
        }

        private static void writeTimeline(Utf8JsonBuffer out, TimelineEvent event) {
            out.writeRaw(TIMELINE_STATUS).writeString(event.status());
            out.writeRaw(AT).writeString(event.at());
            out.writeByte('}');
        }

        private static byte[] ascii(String value) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final class SimpleInputMessage implements HttpInputMessage {
        private final byte[] body;
        private final HttpHeaders headers = new HttpHeaders();

        private SimpleInputMessage(byte[] body) {
            this.body = body;
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(body.length);
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class SimpleOutputMessage implements HttpOutputMessage {
        private final ObservedByteArrayOutputStream body = new ObservedByteArrayOutputStream(2048);
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        private byte[] bytes() {
            return body.toByteArray();
        }

        private int size() {
            return body.size();
        }

        private byte firstByte() {
            return body.firstByte();
        }

        private byte lastByte() {
            return body.lastByte();
        }
    }

    private static final class SinkOutputMessage implements HttpOutputMessage {
        private final ObservedSinkOutputStream body = new ObservedSinkOutputStream();
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        private int size() {
            return body.size();
        }

        private byte firstByte() {
            return body.firstByte();
        }

        private byte lastByte() {
            return body.lastByte();
        }
    }

    private static final class ObservedSinkOutputStream extends OutputStream {
        private int size;
        private byte first;
        private byte last;

        @Override
        public void write(int value) {
            byte next = (byte) value;
            if (size == 0) {
                first = next;
            }
            last = next;
            size++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (length <= 0) {
                return;
            }
            if (size == 0) {
                first = bytes[offset];
            }
            last = bytes[offset + length - 1];
            size += length;
        }

        private int size() {
            return size;
        }

        private byte firstByte() {
            return size == 0 ? 0 : first;
        }

        private byte lastByte() {
            return size == 0 ? 0 : last;
        }
    }

    private static final class ObservedByteArrayOutputStream extends ByteArrayOutputStream {
        private ObservedByteArrayOutputStream(int size) {
            super(size);
        }

        private byte firstByte() {
            return count == 0 ? 0 : buf[0];
        }

        private byte lastByte() {
            return count == 0 ? 0 : buf[count - 1];
        }
    }
}
