package io.jsonfastlane;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class JsonShapeScanner {
    private JsonShapeScanner() {
    }

    static JsonShapeObservation scan(byte[] utf8Json) {
        Parser parser = new Parser(new String(utf8Json, StandardCharsets.UTF_8));
        return parser.scan(utf8Json.length);
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json;
        }

        private JsonShapeObservation scan(int payloadBytes) {
            skipWhitespace();
            if (isAtEnd()) {
                return new JsonShapeObservation(payloadBytes, JsonValueKind.UNKNOWN, List.of(), "");
            }

            char current = json.charAt(index);
            if (current == '{') {
                List<FieldObservation> fields = scanRootObject();
                return new JsonShapeObservation(payloadBytes, JsonValueKind.OBJECT, fields, signature(fields));
            }
            if (current == '[') {
                skipArray();
                return new JsonShapeObservation(payloadBytes, JsonValueKind.ARRAY, List.of(), "");
            }

            JsonValueKind rootKind = skipValue();
            return new JsonShapeObservation(payloadBytes, rootKind, List.of(), "");
        }

        private List<FieldObservation> scanRootObject() {
            List<FieldObservation> fields = new ArrayList<>();
            expect('{');
            skipWhitespace();
            int position = 0;

            if (peek('}')) {
                index++;
                return fields;
            }

            while (!isAtEnd()) {
                skipWhitespace();
                String name = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                JsonValueKind kind = skipValue();
                fields.add(new FieldObservation(name, kind, position++));
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }
                if (peek('}')) {
                    index++;
                    return fields;
                }

                throw error("Expected ',' or '}'");
            }

            throw error("Unterminated object");
        }

        private JsonValueKind skipValue() {
            skipWhitespace();
            if (isAtEnd()) {
                return JsonValueKind.UNKNOWN;
            }

            char current = json.charAt(index);
            return switch (current) {
                case '"' -> {
                    readString();
                    yield JsonValueKind.STRING;
                }
                case '{' -> {
                    skipObject();
                    yield JsonValueKind.OBJECT;
                }
                case '[' -> {
                    skipArray();
                    yield JsonValueKind.ARRAY;
                }
                case 't', 'f' -> {
                    skipBoolean();
                    yield JsonValueKind.BOOLEAN;
                }
                case 'n' -> {
                    skipNull();
                    yield JsonValueKind.NULL;
                }
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        skipNumber();
                        yield JsonValueKind.NUMBER;
                    }
                    throw error("Unexpected value");
                }
            };
        }

        private void skipObject() {
            expect('{');
            skipWhitespace();

            if (peek('}')) {
                index++;
                return;
            }

            while (!isAtEnd()) {
                skipWhitespace();
                readString();
                skipWhitespace();
                expect(':');
                skipValue();
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }
                if (peek('}')) {
                    index++;
                    return;
                }

                throw error("Expected ',' or '}'");
            }

            throw error("Unterminated object");
        }

        private void skipArray() {
            expect('[');
            skipWhitespace();

            if (peek(']')) {
                index++;
                return;
            }

            while (!isAtEnd()) {
                skipValue();
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    continue;
                }
                if (peek(']')) {
                    index++;
                    return;
                }

                throw error("Expected ',' or ']'");
            }

            throw error("Unterminated array");
        }

        private String readString() {
            expect('"');
            StringBuilder builder = null;
            int start = index;

            while (!isAtEnd()) {
                char current = json.charAt(index++);
                if (current == '"') {
                    int end = index - 1;
                    if (builder == null) {
                        return json.substring(start, end);
                    }
                    builder.append(json, start, end);
                    return builder.toString();
                }

                if (current == '\\') {
                    if (builder == null) {
                        builder = new StringBuilder();
                    }
                    builder.append(json, start, index - 1);
                    builder.append(readEscapedCharacter());
                    start = index;
                }
            }

            throw error("Unterminated string");
        }

        private char readEscapedCharacter() {
            if (isAtEnd()) {
                throw error("Unterminated escape sequence");
            }

            char escaped = json.charAt(index++);
            return switch (escaped) {
                case '"', '\\', '/' -> escaped;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicodeEscape();
                default -> throw error("Invalid escape sequence");
            };
        }

        private char readUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw error("Invalid unicode escape");
            }

            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(json.charAt(index++), 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private void skipBoolean() {
            if (json.startsWith("true", index)) {
                index += 4;
                return;
            }
            if (json.startsWith("false", index)) {
                index += 5;
                return;
            }
            throw error("Invalid boolean");
        }

        private void skipNull() {
            if (!json.startsWith("null", index)) {
                throw error("Invalid null");
            }
            index += 4;
        }

        private void skipNumber() {
            if (peek('-')) {
                index++;
            }
            readDigits();
            if (peek('.')) {
                index++;
                readDigits();
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                readDigits();
            }
        }

        private void readDigits() {
            int start = index;
            while (!isAtEnd() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("Expected digit");
            }
        }

        private void skipWhitespace() {
            while (!isAtEnd()) {
                char current = json.charAt(index);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    index++;
                    continue;
                }
                return;
            }
        }

        private void expect(char expected) {
            if (isAtEnd() || json.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return !isAtEnd() && json.charAt(index) == expected;
        }

        private boolean isAtEnd() {
            return index >= json.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }

    private static String signature(List<FieldObservation> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(fields.get(i).name());
        }
        return builder.toString();
    }
}
