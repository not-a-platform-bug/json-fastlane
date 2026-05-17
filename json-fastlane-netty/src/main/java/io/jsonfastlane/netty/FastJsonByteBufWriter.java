package io.jsonfastlane.netty;

import io.netty.buffer.ByteBuf;

public interface FastJsonByteBufWriter<T> {
    void write(T value, ByteBuf out);
}
