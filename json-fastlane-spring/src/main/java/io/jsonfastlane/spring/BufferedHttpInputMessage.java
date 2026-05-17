package io.jsonfastlane.spring;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

final class BufferedHttpInputMessage implements HttpInputMessage {
    private final byte[] body;
    private final HttpHeaders headers;

    BufferedHttpInputMessage(byte[] body, HttpHeaders headers) {
        this.body = body;
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
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
