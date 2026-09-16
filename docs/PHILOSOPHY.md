# The Philosophy of FastTouch

> [!IMPORTANT]
> **"Keine Kopien. Niemals. Kritischer JNI-Pfad. Native-First Performance."**

FastTouch is built on the principle that modern touchscreen and pen-enabled Java applications require **hardware-native** digitizer access that standard AWT and Swing architectures fail to expose.

## Core Tenets

1.  **Native-First Execution**
    Bypass standard Java mouse-emulation layers to intercept raw multi-point digitizer packets directly from Windows `WM_POINTER` APIs.

2.  **True Multi-Touch Disambiguation**
    Enable concurrent tracking of 10+ fingers with distinct contact IDs, pressure values, and physical contact areas without coalescing into synthetic single-cursor events.

3.  **Deterministic Latency**
    Deliver high-rate (120 Hz+) touch telemetry directly to application listeners without queuing bottlenecks or UI thread stalls.

4.  **Hardware-Aware Optimization**
    Capture raw digitizer hardware metrics (contact bounding boxes, force levels, orientation, and tilt) directly from device drivers.

5.  **Blueprint Consistency**
    As part of the **FastJava** ecosystem, FastTouch adheres to a standardized architecture:
    *   **Native Backend**: Direct Win32 C++ implementation.
    *   **Unified Loading**: Powered by `FastCore`.
    *   **Premium Quality**: Built for high-performance creative tools, kiosk interfaces, and autonomous agent input pipelines.

---
**⚡ FastTouch — Powering the next generation of Native Java.**
