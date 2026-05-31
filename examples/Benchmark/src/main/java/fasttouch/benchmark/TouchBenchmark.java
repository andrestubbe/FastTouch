package fasttouch.benchmark;

import fasttouch.FastGesture;
import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;
import fasttouch.FastTouch.State;
import org.openjdk.jmh.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class TouchBenchmark {

    private FastGesture fastGesture;
    private Map<Integer, TouchPoint> touches;

    @Setup(Level.Iteration)
    public void setup() {
        fastGesture = new FastGesture(new FastGesture.GestureListener() {
            @Override public void onPan(float dx, float dy, float x, float y) {}
            @Override public void onPinchStart(float centerX, float centerY) {}
            @Override public void onPinchUpdate(float scale, float angleDelta, float distance, float centerX, float centerY) {}
            @Override public void onPinchEnd() {}
        }, point -> true);

        touches = new HashMap<>();
        // Simulate two fingers moving
        touches.put(0, new TouchPoint(0, 100, 100, 128, 10, 10, System.currentTimeMillis(), State.MOVE));
        touches.put(1, new TouchPoint(1, 200, 200, 128, 10, 10, System.currentTimeMillis(), State.MOVE));
    }

    @Benchmark
    public void benchmarkGestureMath() {
        // Measures the mathematical overhead of calculating pan, pinch, and rotation from raw touch coordinates
        fastGesture.update(touches);
    }
}
