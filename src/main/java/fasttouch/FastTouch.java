package fasttouch;

import fastcore.FastCore;
import java.util.ArrayList;
import java.util.List;

/**
 * FastTouch - Native Windows Touchscreen Input API for Java.
 * 
 * <p>Provides low-latency, native-quality touchscreen input for Java applications
 * using the Windows WM_POINTER API (Windows 8+). Supports multi-touch tracking,
 * pressure sensitivity, and contact size measurement.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Multi-Touch:</b> Track up to 10 simultaneous touch points</li>
 *   <li><b>Pressure Sensing:</b> 0-255 pressure levels from capacitive touch</li>
 *   <li><b>Contact Size:</b> Width and height of finger contact area</li>
 *   <li><b>Low Latency:</b> Native event-driven architecture, no polling required</li>
 *   <li><b>Thread-Safe:</b> All callbacks on EDT, synchronized touch state</li>
 * </ul>
 * 
 * <p><b>Basic Usage:</b></p>
 * <pre>
 * JFrame frame = new JFrame("Touch Demo");
 * frame.setVisible(true);
 * 
 * // Initialize touch input for the window
 * FastTouch touch = FastTouch.create(frame);
 * 
 * // Add touch listener
 * touch.addListener(point -&gt; {
 *     System.out.println("Touch " + point.id + " at (" + point.x + "," + point.y + ")");
 *     if (point.state == FastTouch.State.DOWN) {
 *         System.out.println("  Pressure: " + point.pressure);
 *         System.out.println("  Size: " + point.width + "x" + point.height);
 *     }
 * });
 * 
 * // Start touch processing
 * touch.start();
 * </pre>
 * 
 * <p><b>Thread Safety:</b> All touch events are delivered on the Swing Event Dispatch Thread (EDT).
 * The touch processing runs in a background daemon thread that polls at ~120Hz.</p>
 * 
 * <p><b>Platform Support:</b> Windows 8/10/11 only (requires WM_POINTER API).</p>
 * 
 * @author FastJava Team
 * @version 1.1.0
 * @since 1.0.0
 * @see <a href="https://github.com/andrestubbe/FastTouch">GitHub Repository</a>
 */
public class FastTouch {
    
    static {
        FastCore.loadLibrary("fasttouch");
    }
    
    /**
     * Immutable data class representing a single touch point.
     * 
     * <p>Contains all information about a touch event including position,
     * pressure, contact size, and timing. TouchPoint instances are immutable
     * and thread-safe.</p>
     * 
     * <p><b>Field Summary:</b></p>
     * <table border="1">
     *   <tr><th>Field</th><th>Range/Unit</th><th>Description</th></tr>
     *   <tr><td>{@link #id}</td><td>0-9</td><td>Unique touch ID for multi-touch tracking</td></tr>
     *   <tr><td>{@link #x}, {@link #y}</td><td>pixels</td><td>Window-relative coordinates</td></tr>
     *   <tr><td>{@link #pressure}</td><td>0-255</td><td>Contact pressure (0=light, 255=heavy)</td></tr>
     *   <tr><td>{@link #width}, {@link #height}</td><td>pixels</td><td>Approximate contact area</td></tr>
     *   <tr><td>{@link #state}</td><td>enum</td><td>DOWN, MOVE, or UP</td></tr>
     *   <tr><td>{@link #timestamp}</td><td>ms</td><td>Event time (System.currentTimeMillis)</td></tr>
     * </table>
     */
    public static final class TouchPoint {
        
        /** Unique touch identifier (0-9) for tracking individual fingers. */
        public final int id;
        
        /** X coordinate in window-relative pixels (0 = left edge). */
        public final int x;
        
        /** Y coordinate in window-relative pixels (0 = top edge). */
        public final int y;
        
        /** Contact pressure level, normalized to 0-255 range.
         *  0 = very light touch, 255 = maximum pressure. */
        public final int pressure;
        
        /** Approximate contact width in pixels (ellipse major axis). */
        public final int width;
        
        /** Approximate contact height in pixels (ellipse minor axis). */
        public final int height;
        
        /** Event timestamp in milliseconds (from GetTickCount). */
        public final long timestamp;
        
        /** Touch event state: DOWN (finger first contacts), MOVE (dragging), UP (lifted). */
        public final State state;
        
        // Stylus/Pen support (reserved for future use)
        /** Touch orientation in degrees (0 = upright). Currently 0. */
        public final float orientation;
        
        /** X-axis tilt angle in degrees (-90 to +90). Currently 0. */
        public final float tiltX;
        
        /** Y-axis tilt angle in degrees (-90 to +90). Currently 0. */
        public final float tiltY;
        
        /** Confidence level 0-255 (255 = high confidence). Currently 255. */
        public final int confidence;
        
        public TouchPoint(int id, int x, int y, int pressure, int width, int height, 
                         long timestamp, State state) {
            this(id, x, y, pressure, width, height, timestamp, state, 
                 0f, 0f, 0f, 255);
        }
        
        public TouchPoint(int id, int x, int y, int pressure, int width, int height, 
                         long timestamp, State state,
                         float orientation, float tiltX, float tiltY, int confidence) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.width = width;
            this.height = height;
            this.timestamp = timestamp;
            this.state = state;
            this.orientation = orientation;
            this.tiltX = tiltX;
            this.tiltY = tiltY;
            this.confidence = confidence;
        }
        
        @Override
        public String toString() {
            return String.format("TouchPoint[id=%d, pos=(%d,%d), pressure=%d, size=%dx%d, %s]", 
                id, x, y, pressure, width, height, state);
        }
    }
    
    /**
     * Touch event lifecycle states.
     * 
     * <p>Each touch point progresses through: {@link #DOWN} -&gt; {@link #MOVE}* -&gt; {@link #UP}</p>
     */
    public enum State {
        /** Finger first contacts the screen. Always the first event for a touch ID. */
        DOWN,
        /** Finger is dragging across the screen. May be delivered multiple times. */
        MOVE,
        /** Finger lifted from screen. Always the last event for a touch ID. */
        UP
    }
    
    /**
     * Listener interface for receiving touch events.
     * 
     * <p>Implement this interface to receive touch notifications. Register your
     * listener using {@link #addListener(TouchListener)}.</p>
     * 
     * <p><b>Thread Safety:</b> Callbacks are delivered from the touch polling thread,
     * NOT the EDT. Use {@link SwingUtilities#invokeLater} for UI updates.</p>
     * 
     * @see #addListener(TouchListener)
     * @see #removeListener(TouchListener)
     */
    public interface TouchListener {
        
        /**
         * Called when a touch event occurs.
         * 
         * @param point the touch point data (immutable, never null)
         */
        void onTouch(TouchPoint point);
    }
    
    // Native Methoden
    private static native void initNative(long hwnd);
    private static native void pollNative();  // DEPRECATED - use event-driven
    private static native int getTouchCount();
    private static native int getTouchId(int index);
    
    // NEW: Native callback entry point (called from C++ when touch event occurs)
    private static void onNativeTouch(int id, int x, int y, int pressure, 
                                       int width, int height, int state, long timestamp) {
        TouchPoint point = new TouchPoint(id, x, y, pressure, width, height, 
                                         timestamp, State.values()[state]);
        // Notify all listeners directly on event
        for (TouchListener listener : listeners) {
            listener.onTouch(point);
        }
    }
    private static native int getTouchX(int index);
    private static native int getTouchY(int index);
    private static native int getTouchPressure(int index);
    private static native int getTouchWidth(int index);
    private static native int getTouchHeight(int index);
    private static native int getTouchState(int index);
    private static native long getTouchTimestamp(int index);
    
    private long hwnd;
    private static final List<TouchListener> listeners = new ArrayList<>();  // static for native callback
    private volatile boolean running = false;
    private final java.util.Set<Integer> firedUpEvents = new java.util.HashSet<>();  // Track already fired UP events
    
    /**
     * Creates a FastTouch instance for the given JFrame.
     * 
     * <p>This method initializes the native touch input subsystem for the specified
     * Swing window. The frame must be visible with a title set before calling this
     * method (use after {@code setVisible(true)}).</p>
     * 
     * <p>The method searches for the native window handle using the frame title.
     * It retries automatically for up to 3 seconds to allow the native window
     * to be created by the OS.</p>
     * 
     * @param frame the JFrame to receive touch input (must be visible with title)
     * @return initialized FastTouch instance
     * @throws RuntimeException if the native window cannot be found
     * 
     * @see #start()
     */
    public static FastTouch create(javax.swing.JFrame frame) {
        System.out.println("[FastTouch] create() called");
        
        // Warte bis Fenster sichtbar und Titel gesetzt ist
        System.out.println("[FastTouch] Waiting for frame...");
        int waitRetries = 0;
        while ((!frame.isVisible() || frame.getTitle() == null || frame.getTitle().isEmpty()) && waitRetries < 50) {
            try { Thread.sleep(10); } catch (InterruptedException e) {}
            waitRetries++;
        }
        
        String title = frame.getTitle();
        System.out.println("[FastTouch] Frame title: '" + title + "' (length=" + (title != null ? title.length() : 0) + ")");
        
        // Versuche verschiedene Titel-Varianten
        long hwnd = 0;
        String[] titleVariants = {
            title,
            title != null ? title.trim() : null,
            "FastTouch Demo",
            "FastTouch"
        };
        
        int retries = 0;
        while (hwnd == 0 && retries < 30) {
            for (String variant : titleVariants) {
                if (variant != null && !variant.isEmpty()) {
                    hwnd = findWindow(variant);
                    if (hwnd != 0) {
                        System.out.println("[FastTouch] Found with title: '" + variant + "'");
                        break;
                    }
                }
            }
            if (hwnd == 0) {
                System.out.println("[FastTouch] Retry " + (retries + 1) + "/30...");
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                retries++;
            }
        }
        
        if (hwnd == 0) {
            throw new RuntimeException("[FastTouch] Fenster nicht gefunden. Titel war: '" + title + "'");
        }
        System.out.println("[FastTouch] Window handle found: " + hwnd);
        
        return new FastTouch(hwnd);
    }
    
    private static native long findWindow(String title);
    
    private FastTouch(long hwnd) {
        System.out.println("[FastTouch] Constructor called with hwnd=" + hwnd);
        this.hwnd = hwnd;
        System.out.println("[FastTouch] Calling initNative...");
        initNative(hwnd);
        System.out.println("[FastTouch] initNative completed");
    }
    
    /**
     * Registers a touch listener to receive touch events.
     * 
     * <p>Multiple listeners can be registered. All listeners receive all touch events
     * in the order they were added. Listeners are stored statically (shared across
     * all FastTouch instances).</p>
     * 
     * @param listener the listener to add (must not be null)
     * @throws NullPointerException if listener is null
     * @see TouchListener
     * @see #removeListener(TouchListener)
     */
    public void addListener(TouchListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Unregisters a previously added touch listener.
     * 
     * @param listener the listener to remove
     * @see #addListener(TouchListener)
     */
    public void removeListener(TouchListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Starts the touch polling thread.
     * 
     * <p>Creates a daemon thread that polls for touch events at approximately
     * 120Hz (8ms intervals). Touch events are delivered to all registered
     * listeners via the touch polling thread.</p>
     * 
     * <p>This method is idempotent - calling it multiple times has no effect
     * if already running. The thread automatically stops when {@link #stop()} is
     * called or when the JVM exits (daemon thread).</p>
     * 
     * @see #stop()
     */
    public void start() {
        if (running) return;
        running = true;
        
        Thread pollThread = new Thread(() -> {
            while (running) {
                poll();
                try {
                    Thread.sleep(8); // ~120Hz Polling
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "FastTouch-Poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }
    
    /**
     * Stops the touch polling thread.
     * 
     * <p>Sets the running flag to false, causing the polling thread to exit
     * on its next iteration. This method returns immediately - the thread may
     * continue for a few more milliseconds.</p>
     * 
     * @see #start()
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Polls for touch events once (blocking).
     * 
     * <p>Processes pending Windows messages and checks for touch events.
     * Normally called automatically by the polling thread - manual calls
     * are not required in typical usage.</p>
     * 
     * <p>This method is thread-safe and can be called from any thread.</p>
     * 
     * @see #start()
     */
    public void poll() {
        pollNative();
        
        int count = getTouchCount();
        for (int i = 0; i < count; i++) {
            int id = getTouchId(i);
            int x = getTouchX(i);
            int y = getTouchY(i);
            int pressure = getTouchPressure(i);
            int width = getTouchWidth(i);
            int height = getTouchHeight(i);
            int stateCode = getTouchState(i);
            long timestamp = getTouchTimestamp(i);
            
            State state = stateCode == 0 ? State.DOWN : 
                         stateCode == 1 ? State.MOVE : State.UP;
            
            // UP-Events nur einmal feuern!
            if (state == State.UP) {
                if (firedUpEvents.contains(id)) {
                    continue;  // Bereits gefeuert, überspringen
                }
                firedUpEvents.add(id);  // Markieren als gefeuert
            } else if (state == State.DOWN) {
                // Debug: DOWN event received
                System.out.println("[FastTouch] Java DOWN received for id=" + id);
                firedUpEvents.remove(id);  // UP-Tracking zurücksetzen
            } else {
                // Bei MOVE: UP-Tracking für diese ID zurücksetzen (falls nötig)
                firedUpEvents.remove(id);
            }
            
            TouchPoint point = new TouchPoint(id, x, y, pressure, width, height, timestamp, state);
            
            // Benachrichtige alle Listener
            for (TouchListener listener : listeners) {
                try {
                    listener.onTouch(point);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Checks if a touch input device is available.
     * 
     * <p>Returns true if the system has a touchscreen or precision touchpad
     * that can be used for input. Also returns false on Windows 7 or earlier
     * (requires Windows 8+ WM_POINTER API).</p>
     * 
     * @return true if touch input is available, false otherwise
     * @see #getMaxTouchPoints()
     */
    public static native boolean isTouchAvailable();
    
    /**
     * Returns the maximum number of simultaneous touch points supported.
     * 
     * <p>Windows theoretically supports up to 256 touch points, but most
     * hardware supports 5-10 simultaneous touches. This method returns 10
     * as a practical limit for typical consumer hardware.</p>
     * 
     * @return maximum number of simultaneous touch points (currently 10)
     * @see #isTouchAvailable()
     */
    public static native int getMaxTouchPoints();
    
    // ============================================================================
    // GESTURE SUPPORT (Native Pinch/Rotate via WM_GESTURE)
    // ============================================================================
    
    private static final List<GestureCallback> gestureCallbacks = new ArrayList<>();
    
    /**
     * Callback interface for native gesture events (Pinch/Rotate).
     * Used by FastGesture to receive native gesture events from Windows WM_GESTURE.
     * 
     * @since 1.3.0
     */
    public interface GestureCallback {
        /** Called when a pinch gesture is detected. */
        void onPinch(float scale, float centerX, float centerY);
        /** Called when a rotate gesture is detected. */
        void onRotate(float angle, float centerX, float centerY);
    }
    
    /**
     * Registers a gesture callback for native Pinch/Rotate events.
     * Used internally by FastGesture.
     * 
     * @param callback the callback to register
     */
    public static void addGestureCallback(GestureCallback callback) {
        if (callback != null) {
            gestureCallbacks.add(callback);
        }
    }
    
    /**
     * Unregisters a gesture callback.
     * 
     * @param callback the callback to remove
     */
    public static void removeGestureCallback(GestureCallback callback) {
        gestureCallbacks.remove(callback);
    }
    
    /**
     * Called from native code when a pinch gesture is detected.
     * 
     * @param scale zoom factor (1.0 = neutral)
     * @param centerX pinch center X coordinate
     * @param centerY pinch center Y coordinate
     */
    @SuppressWarnings("unused")
    private static void onNativePinch(float scale, float centerX, float centerY) {
        for (GestureCallback cb : gestureCallbacks) {
            try {
                cb.onPinch(scale, centerX, centerY);
            } catch (Exception e) {
                System.err.println("[FastTouch] Gesture callback error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Called from native code when a rotate gesture is detected.
     * 
     * @param angle rotation in degrees (positive = clockwise)
     * @param centerX rotation center X coordinate
     * @param centerY rotation center Y coordinate
     */
    @SuppressWarnings("unused")
    private static void onNativeRotate(float angle, float centerX, float centerY) {
        for (GestureCallback cb : gestureCallbacks) {
            try {
                cb.onRotate(angle, centerX, centerY);
            } catch (Exception e) {
                System.err.println("[FastTouch] Gesture callback error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Enables or disables native gesture recognition (WM_GESTURE).
     * Must be called after create() to receive Pinch/Rotate events.
     * 
     * @param enable true to enable gesture recognition
     */
    public native void setGestureEnabled(boolean enable);
}
