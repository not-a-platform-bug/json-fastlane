package io.jsonfastlane;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public final class Utf8JsonBuffer {
    private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE = {'f', 'a', 'l', 's', 'e'};
    private static final byte[] NULL = {'n', 'u', 'l', 'l'};
    private static final byte[] ESCAPED_QUOTE = {'\\', '"'};
    private static final byte[] ESCAPED_BACKSLASH = {'\\', '\\'};
    private static final byte[] ESCAPED_BACKSPACE = {'\\', 'b'};
    private static final byte[] ESCAPED_FORM_FEED = {'\\', 'f'};
    private static final byte[] ESCAPED_NEWLINE = {'\\', 'n'};
    private static final byte[] ESCAPED_CARRIAGE_RETURN = {'\\', 'r'};
    private static final byte[] ESCAPED_TAB = {'\\', 't'};
    private static final byte[] MIN_LONG = {
        '-', '9', '2', '2', '3', '3', '7', '2', '0', '3',
        '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'
    };
    private static final byte[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private byte[] bytes;
    private int size;

    public Utf8JsonBuffer() {
        this(256);
    }

    public Utf8JsonBuffer(int initialCapacity) {
        this.bytes = new byte[initialCapacity];
    }

    public Utf8JsonBuffer writeByte(char value) {
        ensureCapacity(1);
        bytes[size++] = (byte) value;
        return this;
    }

    public Utf8JsonBuffer writeAscii(String value) {
        ensureCapacity(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current > 0x7f) {
                throw new IllegalArgumentException("Non-ASCII character in static JSON fragment");
            }
            bytes[size++] = (byte) current;
        }
        return this;
    }

    public Utf8JsonBuffer writeRaw(byte[] value) {
        ensureCapacity(value.length);
        System.arraycopy(value, 0, bytes, size, value.length);
        size += value.length;
        return this;
    }

    public Utf8JsonBuffer writeLong(long value) {
        if (value == Long.MIN_VALUE) {
            return writeRaw(MIN_LONG);
        }

        if (value == 0) {
            return writeByte('0');
        }

        ensureCapacity(20);
        if (value < 0) {
            bytes[size++] = '-';
            value = -value;
        }

        int start = size;
        while (value > 0) {
            long next = value / 10;
            bytes[size++] = (byte) ('0' + (value - (next * 10)));
            value = next;
        }

        for (int left = start, right = size - 1; left < right; left++, right--) {
            byte swap = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = swap;
        }
        return this;
    }

    public Utf8JsonBuffer writeInt(int value) {
        if (value == Integer.MIN_VALUE) {
            return writeAscii("-2147483648");
        }

        if (value == 0) {
            return writeByte('0');
        }

        ensureCapacity(11);
        if (value < 0) {
            bytes[size++] = '-';
            value = -value;
        }

        int start = size;
        while (value > 0) {
            int next = value / 10;
            bytes[size++] = (byte) ('0' + (value - (next * 10)));
            value = next;
        }

        for (int left = start, right = size - 1; left < right; left++, right--) {
            byte swap = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = swap;
        }
        return this;
    }

    public Utf8JsonBuffer writeBoolean(boolean value) {
        return writeRaw(value ? TRUE : FALSE);
    }

    public Utf8JsonBuffer writeNull() {
        return writeRaw(NULL);
    }

    public Utf8JsonBuffer writeString(String value) {
        if (value == null) {
            return writeNull();
        }

        writeByte('"');
        int length = value.length();
        ensureCapacity(length + 1);
        for (int i = 0; i < length; i++) {
            char current = value.charAt(i);
            if (current >= 0x20 && current != '"' && current != '\\' && current <= 0x7f) {
                bytes[size++] = (byte) current;
            } else {
                i = writeEscapedOrUtf8(value, i, current);
            }
        }
        writeByte('"');
        return this;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(bytes, size);
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(bytes, 0, size);
    }

    public Utf8JsonBuffer reset() {
        size = 0;
        return this;
    }

    public int size() {
        return size;
    }

    public byte firstByte() {
        if (size == 0) {
            return 0;
        }
        return bytes[0];
    }

    public byte lastByte() {
        if (size == 0) {
            return 0;
        }
        return bytes[size - 1];
    }

    private int writeEscapedOrUtf8(String value, int index, char current) {
        switch (current) {
            case '"' -> writeRaw(ESCAPED_QUOTE);
            case '\\' -> writeRaw(ESCAPED_BACKSLASH);
            case '\b' -> writeRaw(ESCAPED_BACKSPACE);
            case '\f' -> writeRaw(ESCAPED_FORM_FEED);
            case '\n' -> writeRaw(ESCAPED_NEWLINE);
            case '\r' -> writeRaw(ESCAPED_CARRIAGE_RETURN);
            case '\t' -> writeRaw(ESCAPED_TAB);
            default -> {
                if (current < 0x20) {
                    writeUnicodeEscape(current);
                } else if (current <= 0x7ff) {
                    ensureCapacity(2);
                    bytes[size++] = (byte) (0xc0 | (current >> 6));
                    bytes[size++] = (byte) (0x80 | (current & 0x3f));
                } else if (Character.isHighSurrogate(current) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                    int codePoint = Character.toCodePoint(current, value.charAt(index + 1));
                    ensureCapacity(4);
                    bytes[size++] = (byte) (0xf0 | (codePoint >> 18));
                    bytes[size++] = (byte) (0x80 | ((codePoint >> 12) & 0x3f));
                    bytes[size++] = (byte) (0x80 | ((codePoint >> 6) & 0x3f));
                    bytes[size++] = (byte) (0x80 | (codePoint & 0x3f));
                    return index + 1;
                } else {
                    ensureCapacity(3);
                    bytes[size++] = (byte) (0xe0 | (current >> 12));
                    bytes[size++] = (byte) (0x80 | ((current >> 6) & 0x3f));
                    bytes[size++] = (byte) (0x80 | (current & 0x3f));
                }
            }
        }
        return index;
    }

    private void writeUnicodeEscape(char value) {
        ensureCapacity(6);
        bytes[size++] = '\\';
        bytes[size++] = 'u';
        bytes[size++] = '0';
        bytes[size++] = '0';
        bytes[size++] = HEX[(value >> 4) & 0xf];
        bytes[size++] = HEX[value & 0xf];
    }

    private void ensureCapacity(int additionalBytes) {
        int required = size + additionalBytes;
        if (required <= bytes.length) {
            return;
        }

        int nextCapacity = bytes.length;
        while (nextCapacity < required) {
            nextCapacity *= 2;
        }
        bytes = Arrays.copyOf(bytes, nextCapacity);
    }
}
