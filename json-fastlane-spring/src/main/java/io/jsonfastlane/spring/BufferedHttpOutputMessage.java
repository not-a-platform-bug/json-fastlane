package io.jsonfastlane.spring;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

final class BufferedHttpOutputMessage implements HttpOutputMessage {
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

    byte[] bodyBytes() {
        return body.toByteArray();
    }
}
