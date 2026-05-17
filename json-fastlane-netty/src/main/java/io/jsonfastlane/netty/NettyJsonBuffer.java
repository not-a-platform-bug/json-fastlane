package io.jsonfastlane.netty;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public final class NettyJsonBuffer {
    private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE = {'f', 'a', 'l', 's', 'e'};
    private static final byte[] NULL = {'n', 'u', 'l', 'l'};
    private static final byte[] MIN_LONG = {
        '-', '9', '2', '2', '3', '3', '7', '2', '0', '3',
        '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'
    };

    private final ByteBuf out;

    public NettyJsonBuffer(ByteBuf out) {
        this.out = out;
    }

    public NettyJsonBuffer writeByte(char value) {
        out.writeByte((byte) value);
        return this;
    }

    public NettyJsonBuffer writeRaw(byte[] value) {
        out.writeBytes(value);
        return this;
    }

    public NettyJsonBuffer writeBoolean(boolean value) {
        return writeRaw(value ? TRUE : FALSE);
    }

    public NettyJsonBuffer writeNull() {
        return writeRaw(NULL);
    }

    public NettyJsonBuffer writeLong(long value) {
        if (value == Long.MIN_VALUE) {
            return writeRaw(MIN_LONG);
        }
        if (value == 0) {
            return writeByte('0');
        }
        if (value < 0) {
            out.writeByte('-');
            value = -value;
        }

        int start = out.writerIndex();
        while (value > 0) {
            long next = value / 10;
            out.writeByte((byte) ('0' + (value - (next * 10))));
            value = next;
        }
        reverse(start, out.writerIndex() - 1);
        return this;
    }

    public NettyJsonBuffer writeInt(int value) {
        return writeLong(value);
    }

    public NettyJsonBuffer writeString(String value) {
        if (value == null) {
            return writeNull();
        }

        out.writeByte('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current >= 0x20 && current != '"' && current != '\\' && current <= 0x7f) {
                out.writeByte((byte) current);
            } else {
                writeSlowStringSuffix(value, i);
                break;
            }
        }
        out.writeByte('"');
        return this;
    }

    private void writeSlowStringSuffix(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> out.writeBytes(new byte[] {'\\', '"'});
                case '\\' -> out.writeBytes(new byte[] {'\\', '\\'});
                case '\n' -> out.writeBytes(new byte[] {'\\', 'n'});
                case '\r' -> out.writeBytes(new byte[] {'\\', 'r'});
                case '\t' -> out.writeBytes(new byte[] {'\\', 't'});
                default -> {
                    if (current < 0x20) {
                        String escape = String.format("\\u%04x", (int) current);
                        out.writeBytes(escape.getBytes(StandardCharsets.US_ASCII));
                    } else {
                        out.writeCharSequence(String.valueOf(current), StandardCharsets.UTF_8);
                    }
                }
            }
        }
    }

    private void reverse(int left, int right) {
        while (left < right) {
            byte swap = out.getByte(left);
            out.setByte(left++, out.getByte(right));
            out.setByte(right--, swap);
        }
    }
}
