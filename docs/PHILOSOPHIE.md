# The Philosophy of FastTouch

> [!IMPORTANT]
> **"Keine Kopien. Niemals. Kritischer JNI-Pfad. Native-First Performance."**

FastTouch is built on the fundamental principle that modern Java UI applications require **native-first** acceleration for input processing. The standard JVM AWT/Swing abstractions are excellent for cross-platform compatibility, but they completely fail at exposing modern hardware capabilities like Multi-Touch, pressure sensitivity, and low-latency pointer tracking.

## Core Tenets of FastTouch

### 1. Hardware-Direct Input (Bypassing AWT)
Java AWT translates touch events into synthesized mouse events (`MOUSE_PRESSED`, `MOUSE_DRAGGED`). This causes lag, drops multi-touch data (since a mouse only has one cursor), and completely loses pressure and contact size information. FastTouch injects a native `GWLP_WNDPROC` hook directly into the Windows Message Loop, intercepting `WM_POINTER` events the exact millisecond they are fired by the touchscreen digitizer.

### 2. Event-Driven, Not Polled
Polling for input burns CPU cycles and creates jitter. FastTouch is entirely event-driven from the C++ layer. The JNI boundary is only crossed when a physical finger actually moves or touches the screen, utilizing a highly optimized asynchronous JNI callback mechanism (`onNativeTouch`).

### 3. Zero-Bottleneck Architecture
The native code tracks the state of up to 10 simultaneous fingers using a pre-allocated static C++ array. No memory is dynamically allocated (`malloc`/`new`) inside the native hot-path. 

### 4. Deterministic Latency
By avoiding garbage collection triggers and object instantiation within the C++ layer, FastTouch guarantees deterministic latency. The time from your finger physically touching the screen to the Java `TouchListener` firing is bounded strictly by the OS and the JNI crossing time (usually under 1ms).

### 5. Blueprint Consistency
As part of the **FastJava** ecosystem, FastTouch adheres to a standardized architecture:
*   **Native Backend**: Direct C++ implementation (Win32 API).
*   **Unified Loading**: Native DLL extraction and loading powered safely by `FastCore`.
*   **Premium Quality**: Built for high-performance systems where every frame counts.

---
**⚡ FastTouch — Unlocking the physical limits of touch hardware for the JVM.**
