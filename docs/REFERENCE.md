# FastTouch Reference Manual

`FastTouch` is the ultra-low latency Win32 `WM_POINTER` multi-touch digitizer, contact sizing, and pressure interception substrate of the FastJava ecosystem.

---

## 1. Core Vocabulary

* **Win32 Pointer Subsystem (`WM_POINTER`)**: Intercepts high-rate hardware touchscreen contacts directly from the Windows 8+ / 10 / 11 Pointer API (`WM_POINTERDOWN`, `WM_POINTERUPDATE`, `WM_POINTERUP`), bypassing legacy single-cursor mouse emulation.
* **Window Subclassing**: Integrates natively with `HWND` via `SetWindowLongPtr(GWLP_WNDPROC)` to intercept pointer messages before the standard Java AWT window procedure.
* **Pressure Normalization**: Converts raw 10-bit Windows digitizer force levels (`0..1024`) into standard normalized byte ranges (`0..255`).
* **Contact Geometry**: Reports exact physical bounding-box dimensions (`width` and `height` in pixels) derived from the hardware contact matrix (`POINTER_TOUCH_INFO.rcContact`).

---

## 2. Java API Reference

### Class: `fasttouch.FastTouch`
The primary class managing touchscreen lifecycle, window hooks, and event polling.

#### Factory Methods
- `public static FastTouch create(javax.swing.JFrame frame)`  
  Resolves the native `HWND` of the provided frame and installs the native `WM_POINTER` window procedure hook.

#### Event Capture & Polling
- `public void addListener(TouchListener listener)`  
  Registers a callback listener for real-time touch point dispatch.
- `public void removeListener(TouchListener listener)`  
  Removes a previously registered touch listener.
- `public void start()`  
  Spawns a dedicated background daemon polling thread running at ~120 Hz to pump native messages and notify listeners.
- `public void stop()`  
  Halts the background polling loop.
- `public void poll()`  
  Executes a single polling iteration (`PeekMessage` / `DispatchMessage`), updates active touch slots, and fires listener callbacks.

#### Hardware Capabilities
- `public static native boolean isTouchAvailable()`  
  Queries whether an active hardware touchscreen or multi-touch digitizer is connected to the system.
- `public static native int getMaxTouchPoints()`  
  Retrieves the maximum number of simultaneous touch contact points supported by the host hardware (typically 10+ points).

---

### Class: `fasttouch.FastTouch.TouchPoint`
Immutable representation of an active touch contact point on the screen:

- `public final int id` — Unique hardware touch point identifier for simultaneous multi-touch tracking.
- `public final int x, y` — Client coordinates in pixels relative to the window.
- `public final int pressure` — Normalized contact pressure force (`0` to `255`).
- `public final int width, height` — Contact patch bounding box dimensions in pixels.
- `public final long timestamp` — System message timestamp in milliseconds.
- `public final State state` — Current phase: `State.DOWN`, `State.MOVE`, or `State.UP`.
- `public final float orientation` — Touch contact orientation angle in degrees.
- `public final float tiltX, tiltY` — Digitizer tilt angles in degrees (when supported by pen/stylus digitizers).
- `public final int confidence` — Hardware confidence level (`0` to `255`).

---

### Interface: `fasttouch.FastTouch.TouchListener`
High-frequency functional interface receiving real-time touch point state updates:

```java
@FunctionalInterface
public interface TouchListener {
    void onTouch(TouchPoint point);
}
```

---

## 3. Platform & Hardware Guarantees

* **Multi-Touch Support**: Up to 10+ concurrent physical touch contacts tracked simultaneously with independent IDs.
* **Pressure Sensitivity**: True hardware force capture without synthetic interpolation.
* **Contact Size Resolution**: Hardware contact area dimensions available on every touch frame.
* **Zero JVM Queue Stalls**: Direct native hook dispatch avoids delays from the standard AWT event queue.

---

**Part of the FastJava Ecosystem** — *Making the JVM faster.* 🚀
