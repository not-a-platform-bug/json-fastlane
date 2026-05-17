package io.jsonfastlane.spring;

public final class JsonFastlaneSpring {
    private static final ThreadLocal<String> CURRENT_ENDPOINT = new ThreadLocal<>();

    private JsonFastlaneSpring() {
    }

    public static void setCurrentEndpoint(String endpoint) {
        CURRENT_ENDPOINT.set(endpoint);
    }

    public static EndpointScope withCurrentEndpoint(String endpoint) {
        String previous = CURRENT_ENDPOINT.get();
        CURRENT_ENDPOINT.set(endpoint);
        return new EndpointScope(previous);
    }

    public static void clearCurrentEndpoint() {
        CURRENT_ENDPOINT.remove();
    }

    static String currentEndpoint() {
        String endpoint = CURRENT_ENDPOINT.get();
        return endpoint == null ? "unknown" : endpoint;
    }

    public static final class EndpointScope implements AutoCloseable {
        private final String previous;

        private EndpointScope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT_ENDPOINT.remove();
            } else {
                CURRENT_ENDPOINT.set(previous);
            }
        }
    }
}
