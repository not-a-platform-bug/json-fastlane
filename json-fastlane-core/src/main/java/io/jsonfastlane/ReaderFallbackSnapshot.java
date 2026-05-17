package io.jsonfastlane;

public record ReaderFallbackSnapshot(long fastReads, long fallbackReads) {
    public long totalReads() {
        return fastReads + fallbackReads;
    }

    public double fallbackRate() {
        long total = totalReads();
        return total == 0 ? 0.0 : (double) fallbackReads / total;
    }
}
