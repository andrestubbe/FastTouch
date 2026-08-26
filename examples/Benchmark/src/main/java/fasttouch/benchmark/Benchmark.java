package fasttouch.benchmark;

import fasttouch.FastTouch;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class Benchmark {

    private FastTouch.TouchPoint samplePoint;

    @Setup
    public void setup() {
        samplePoint = new FastTouch.TouchPoint(
            1, 100, 200, 128, 10, 10, System.currentTimeMillis(), FastTouch.State.MOVE
        );
    }

    @org.openjdk.jmh.annotations.Benchmark
    public FastTouch.TouchPoint benchmarkTouchPointAllocation() {
        return new FastTouch.TouchPoint(
            1, 100, 200, 128, 10, 10, System.currentTimeMillis(), FastTouch.State.MOVE
        );
    }

    @org.openjdk.jmh.annotations.Benchmark
    public String benchmarkTouchPointFormatting() {
        return samplePoint.toString();
    }
}
