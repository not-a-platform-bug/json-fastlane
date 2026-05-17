package io.jsonfastlane.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonfastlane.Utf8JsonBuffer;
import io.jsonfastlane.netty.NettyJsonBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Fork(1)
@State(Scope.Thread)
public class JsonFastPathBenchmark {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Utf8JsonBuffer buffer = new Utf8JsonBuffer(256);
    private final ByteBuf byteBuf = Unpooled.buffer(256);
    private final NettyJsonBuffer nettyBuffer = new NettyJsonBuffer(byteBuf);
    private SampleResponse response;

    @Setup(Level.Trial)
    public void setup() {
        response = new SampleResponse(42, "PAID", "kim@example.com", 173200);
    }

    @Benchmark
    public byte[] jacksonWriteValueAsBytes() throws Exception {
        return objectMapper.writeValueAsBytes(response);
    }

    @Benchmark
    public byte[] fastlaneWriteByteArray() {
        buffer.reset();
        writeResponse(buffer, response);
        return buffer.toByteArray();
    }

    @Benchmark
    public Utf8JsonBuffer fastlaneWriteReusableBuffer() {
        buffer.reset();
        writeResponse(buffer, response);
        return buffer;
    }

    @Benchmark
    public ByteBuf fastlaneWriteNettyByteBuf() {
        byteBuf.clear();
        writeResponse(nettyBuffer, response);
        return byteBuf;
    }

    private static void writeResponse(Utf8JsonBuffer out, SampleResponse response) {
        out.writeRaw("{\"orderId\":".getBytes())
            .writeLong(response.orderId())
            .writeRaw(",\"status\":".getBytes())
            .writeString(response.status())
            .writeRaw(",\"email\":".getBytes())
            .writeString(response.email())
            .writeRaw(",\"totalCents\":".getBytes())
            .writeLong(response.totalCents())
            .writeByte('}');
    }

    private static void writeResponse(NettyJsonBuffer out, SampleResponse response) {
        out.writeRaw("{\"orderId\":".getBytes())
            .writeLong(response.orderId())
            .writeRaw(",\"status\":".getBytes())
            .writeString(response.status())
            .writeRaw(",\"email\":".getBytes())
            .writeString(response.email())
            .writeRaw(",\"totalCents\":".getBytes())
            .writeLong(response.totalCents())
            .writeByte('}');
    }

    public record SampleResponse(long orderId, String status, String email, long totalCents) {
    }
}
