package fasttouch;

import java.util.ArrayList;
import java.util.List;

/**
 * FastTouch - Native Touchscreen Input für Java
 * 
 * Erfasst Touch-Events mit nativer Windows API via JNI.
 * Unterstützt Multi-Touch mit Koordinaten und Pressure.
 */
public class FastTouch {
    
    static {
        System.loadLibrary("FastTouch");
    }
    
    /**
     * Einzelner Touch-Point - immutable data class
     */
    public static final class TouchPoint {
        public final int id;           // Touch-ID (für Multi-Touch Tracking)
        public final int x;            // X-Koordinate
        public final int y;            // Y-Koordinate
        public final int pressure;       // Pressure (0-255)
        public final int width;          // Contact width in pixels
        public final int height;         // Contact height in pixels
        public final long timestamp;     // Zeitstempel in ms
        public final State state;        // DOWN, MOVE, UP
        
        // Optional: für Stylus/Pen später
        public final float orientation;   // Touch/pen orientation (degrees)
        public final float tiltX;         // Tilt X angle (degrees)
        public final float tiltY;         // Tilt Y angle (degrees)
        public final int confidence;      // Confidence level (0-255)
        
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
    
    public enum State { DOWN, MOVE, UP }
    
    // Native Methoden
    private static native void initNative(long hwnd);
    private static native void pollNative();
    private static native int getTouchCount();
    private static native int getTouchId(int index);
    private static native int getTouchX(int index);
    private static native int getTouchY(int index);
    private static native int getTouchPressure(int index);
    private static native int getTouchWidth(int index);
    private static native int getTouchHeight(int index);
    private static native int getTouchState(int index);
    private static native long getTouchTimestamp(int index);
    
    private long hwnd;
    private final List<TouchListener> listeners = new ArrayList<>();
    private volatile boolean running = false;
    
    /**
     * Interface für Touch-Event Listener
     */
    public interface TouchListener {
        void onTouch(TouchPoint point);
    }
    
    /**
     * Erstellt FastTouch für ein JFrame/Window
     */
    public static FastTouch create(javax.swing.JFrame frame) {
        System.out.println("[FastTouch] create() called");
        
        // Warte bis Fenster sichtbar
        System.out.println("[FastTouch] Waiting for frame to be visible...");
        while (!frame.isVisible()) {
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
        
        String title = frame.getTitle();
        System.out.println("[FastTouch] Frame title: " + title);
        if (title == null || title.isEmpty()) {
            title = "FastTouch";
        }
        
        System.out.println("[FastTouch] Finding window handle...");
        long hwnd = findWindow(title);
        int retries = 0;
        while (hwnd == 0 && retries < 20) {
            System.out.println("[FastTouch] Retry " + (retries + 1) + "/20 finding window...");
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow(title);
            retries++;
        }
        
        if (hwnd == 0) {
            throw new RuntimeException("[FastTouch] Fenster nicht gefunden: " + title);
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
     * Fügt einen Touch-Listener hinzu
     */
    public void addListener(TouchListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Entfernt einen Touch-Listener
     */
    public void removeListener(TouchListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Startet den Touch-Polling-Thread
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
     * Stoppt den Touch-Polling-Thread
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Einmaliges Pollen (blocking)
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
     * Prüft ob Touch verfügbar ist (Touchscreen vorhanden)
     */
    public static native boolean isTouchAvailable();
    
    /**
     * Maximale gleichzeitige Touch-Points
     */
    public static native int getMaxTouchPoints();
}
