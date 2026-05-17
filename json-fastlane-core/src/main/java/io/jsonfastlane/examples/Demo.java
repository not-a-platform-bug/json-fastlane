package io.jsonfastlane.examples;

import io.jsonfastlane.EndpointProfileSnapshot;
import io.jsonfastlane.FieldProfileSnapshot;
import io.jsonfastlane.JsonFastlane;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Demo {
    private Demo() {
    }

    public static void main(String[] args) {
        JsonFastlane fastlane = new JsonFastlane();
        fastlane.record("/orders", """
            {"userId":42,"items":[{"sku":"tea","quantity":2}],"couponCode":null}
            """);
        fastlane.record("/orders", """
            {"userId":43,"items":[{"sku":"mug","quantity":1}],"couponCode":"SPRING"}
            """);

        for (EndpointProfileSnapshot endpoint : fastlane.snapshots()) {
            System.out.println(endpoint.endpoint() + " samples=" + endpoint.samples()
                + " avgBytes=" + endpoint.averagePayloadBytes());
            for (FieldProfileSnapshot field : endpoint.fields()) {
                System.out.println("  " + field.name() + " kinds=" + field.valueKinds()
                    + " avgPosition=" + String.format("%.1f", field.averagePosition()));
            }
            endpoint.fieldOrders().stream().limit(3).forEach(order ->
                System.out.println("  order=" + order.signature() + " samples=" + order.samples()));
        }

        CreateOrderRequestWriter writer = new CreateOrderRequestWriter();
        byte[] encoded = writer.write(new CreateOrderRequest(
            7,
            List.of(new OrderItemRequest("coffee", 3)),
            "WELCOME"
        ));

        System.out.println(new String(encoded, StandardCharsets.UTF_8));
    }
}
