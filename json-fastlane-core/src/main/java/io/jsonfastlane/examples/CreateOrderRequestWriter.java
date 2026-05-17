package io.jsonfastlane.examples;

import io.jsonfastlane.FastJsonWriter;
import io.jsonfastlane.Utf8JsonBuffer;

public final class CreateOrderRequestWriter implements FastJsonWriter<CreateOrderRequest> {
    @Override
    public byte[] write(CreateOrderRequest value) {
        Utf8JsonBuffer out = new Utf8JsonBuffer();
        out.writeByte('{');
        out.writeAscii("\"userId\":").writeLong(value.userId());
        out.writeAscii(",\"items\":[");
        for (int i = 0; i < value.items().size(); i++) {
            if (i > 0) {
                out.writeByte(',');
            }
            writeItem(out, value.items().get(i));
        }
        out.writeByte(']');
        out.writeAscii(",\"couponCode\":").writeString(value.couponCode());
        out.writeByte('}');
        return out.toByteArray();
    }

    private static void writeItem(Utf8JsonBuffer out, OrderItemRequest item) {
        out.writeByte('{');
        out.writeAscii("\"sku\":").writeString(item.sku());
        out.writeAscii(",\"quantity\":").writeLong(item.quantity());
        out.writeByte('}');
    }
}
