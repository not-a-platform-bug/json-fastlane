package io.jsonfastlane.examples;

import java.util.List;

public record CreateOrderRequest(
    long userId,
    List<OrderItemRequest> items,
    String couponCode
) {
}
