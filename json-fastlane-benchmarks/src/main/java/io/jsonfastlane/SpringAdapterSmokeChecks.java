package io.jsonfastlane;

import io.jsonfastlane.examples.CreateOrderRequest;
import io.jsonfastlane.examples.OrderItemRequest;
import io.jsonfastlane.spring.ConversionDirection;
import io.jsonfastlane.spring.ConversionProfileSnapshot;
import io.jsonfastlane.spring.JsonConversionProfiler;
import io.jsonfastlane.spring.JsonFastlaneSpring;
import io.jsonfastlane.spring.ProfilingJackson2HttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SpringAdapterSmokeChecks {
    private SpringAdapterSmokeChecks() {
    }

    public static void main(String[] args) throws Exception {
        JsonConversionProfiler profiler = new JsonConversionProfiler();
        ProfilingJackson2HttpMessageConverter converter = new ProfilingJackson2HttpMessageConverter(profiler);

        try (JsonFastlaneSpring.EndpointScope ignored = JsonFastlaneSpring.withCurrentEndpoint("/orders")) {
            CreateOrderRequest decoded = (CreateOrderRequest) converter.read(
                CreateOrderRequest.class,
                new SimpleInputMessage("{\"userId\":99,\"items\":[{\"sku\":\"tea\",\"quantity\":2}],\"couponCode\":null}")
            );
            require(decoded.userId() == 99, "decoded user id");
            require(decoded.items().get(0).sku().equals("tea"), "decoded nested sku");

            SimpleOutputMessage output = new SimpleOutputMessage();
            converter.write(
                new CreateOrderRequest(100, List.of(new OrderItemRequest("mug", 1)), "SPRING"),
                MediaType.APPLICATION_JSON,
                output
            );
            String encoded = output.body();
            require(encoded.contains("\"userId\":100"), "encoded user id");
            require(encoded.contains("\"couponCode\":\"SPRING\""), "encoded coupon");
        }

        List<ConversionProfileSnapshot> snapshots = profiler.conversionSnapshots().stream().toList();
        require(snapshots.size() == 2, "conversion snapshots");
        require(snapshots.stream().anyMatch(snapshot -> snapshot.direction() == ConversionDirection.READ), "read snapshot");
        require(snapshots.stream().anyMatch(snapshot -> snapshot.direction() == ConversionDirection.WRITE), "write snapshot");

        EndpointProfileSnapshot shape = profiler.shapeProfiler().snapshots().iterator().next();
        require(shape.endpoint().equals("/orders"), "shape endpoint");
        require(shape.samples() == 2, "shape samples");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Failed: " + label);
        }
    }

    private static final class SimpleInputMessage implements HttpInputMessage {
        private final byte[] body;
        private final HttpHeaders headers = new HttpHeaders();

        private SimpleInputMessage(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(this.body.length);
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
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        private String body() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }
}
