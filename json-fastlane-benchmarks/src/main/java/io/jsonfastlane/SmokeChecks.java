package io.jsonfastlane;

import io.jsonfastlane.examples.CreateOrderRequest;
import io.jsonfastlane.examples.CreateOrderRequestWriter;
import io.jsonfastlane.examples.OrderItemRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SmokeChecks {
    private SmokeChecks() {
    }

    public static void main(String[] args) {
        recordsEndpointShapes();
        writesGeneratedStyleJson();
        System.out.println("Smoke checks passed.");
    }

    private static void recordsEndpointShapes() {
        JsonFastlane fastlane = new JsonFastlane();
        fastlane.record("/orders", "{\"userId\":1,\"items\":[],\"couponCode\":null}");
        fastlane.record("/orders", "{\"userId\":2,\"items\":[],\"couponCode\":\"HELLO\"}");

        EndpointProfileSnapshot snapshot = fastlane.snapshots().iterator().next();
        require(snapshot.samples() == 2, "sample count");
        require(snapshot.fields().size() == 3, "field count");
        require(snapshot.fieldOrders().get(0).signature().equals("userId,items,couponCode"), "field order");

        FieldProfileSnapshot coupon = snapshot.fields().stream()
            .filter(field -> field.name().equals("couponCode"))
            .findFirst()
            .orElseThrow();
        require(coupon.valueKinds().get(JsonValueKind.NULL) == 1, "null coupon count");
        require(coupon.valueKinds().get(JsonValueKind.STRING) == 1, "string coupon count");
    }

    private static void writesGeneratedStyleJson() {
        CreateOrderRequestWriter writer = new CreateOrderRequestWriter();
        byte[] json = writer.write(new CreateOrderRequest(
            10,
            List.of(new OrderItemRequest("cup\nlarge", 2)),
            null
        ));

        String encoded = new String(json, StandardCharsets.UTF_8);
        require(encoded.equals("{\"userId\":10,\"items\":[{\"sku\":\"cup\\nlarge\",\"quantity\":2}],\"couponCode\":null}"),
            "encoded JSON");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Failed: " + label);
        }
    }
}
