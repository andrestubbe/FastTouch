# FastTouch 0.1.0 [ALPHA-2026-05-23] — Ultra-Fast Native Windows Touchscreen & Multi-Touch Engine for Java

[![Status](https://img.shields.io/badge/status-0.1.0-brightgreen.svg)](https://github.com/andrestubbe/FastTouch/releases/tag/0.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-0.1.0-green.svg)](https://jitpack.io/#andrestubbe/FastTouch)

---

**⚡ High-speed Win32 WM_POINTER multi-touch digitizer interception, pressure tracking, and contact bounding-box geometry for Java.**

**FastTouch** provides hardware-level touchscreen and multi-touch digitizer access directly from the Win32 Pointer API (`WM_POINTER`), bypassing the single-cursor mouse emulation limitations of standard AWT and Swing. Track 10+ simultaneous fingers, variable pressure force (`0..255`), and exact physical contact patch dimensions with minimal latency.

[**Watch Showcase Demo (YouTube)**](https://youtu.be/0DzGtGKu5D4)

[![FastTouch Multi-Touch Demo](docs/screenshot.png)](https://youtu.be/0DzGtGKu5D4)

---

## Quick Start

```java
import fasttouch.FastTouch;
import javax.swing.JFrame;

public class Demo {
    public static void main(String[] args) {
        JFrame frame = new JFrame("FastTouch Demo");
        frame.setSize(1280, 800);
        frame.setVisible(true);

        // 1. Initialize native touch interception on the target window
        FastTouch touch = FastTouch.create(frame);

        // 2. Add high-rate multi-touch listener
        touch.addListener(point -> {
            System.out.printf("[TOUCH] ID=%d Pos=(%d,%d) Pressure=%d Size=%dx%d Phase=%s\n",
                point.id, point.x, point.y, point.pressure, point.width, point.height, point.state);
        });

        // 3. Start background polling thread (~120 Hz)
        touch.start();
    }
}
```

---

## Table of Contents

- [Quick Start](#quick-start)
- [Why FastTouch?](#why-fasttouch)
- [Key Features](#key-features)
- [Real-World Use Cases](#real-world-use-cases)
- [Performance Benchmarks](#performance-benchmarks)
- [API Quick Reference](#api-quick-reference)
- [Technical Demos & Benchmarks](#technical-demos--benchmarks)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [Related Projects](#related-projects)
- [License](#license)

---

## Why FastTouch?

Standard Java input subsystems (AWT `MouseListener`, JavaFX, Swing) fundamentally treat touchscreens as simulated single-point mouse cursors:

- **Single-Cursor Emulation**: Standard AWT collapses multiple finger touches into a single cursor, discarding all concurrent multi-touch data.
- **Missing Pressure & Size**: Physical force levels and contact area bounding boxes are completely lost in pure Java.
- **Event Queue Delays**: Synthesized mouse events are queued through the Event Dispatch Thread (EDT), creating noticeable tactile lag during fast swipes or gestures.

**FastTouch** bridges directly to the Win32 `WM_POINTER` subsystem:

- **Simultaneous Multi-Touch**: Distinguishes 10+ independent physical touch contacts simultaneously.
- **Hardware Force & Contact Geometry**: Provides true normalized pressure (`0..255`) and contact patch pixel dimensions (`width` x `height`).
- **Low-Latency Polling Pipeline**: Native window subclassing captures pointer messages before the standard Java window procedure.

---

## Key Features

- 🖐️ **True Multi-Touch Interception** — Track 10+ concurrent fingers with individual touch IDs.
- 🎯 **Pressure Sensitivity** — Normalized 0–255 contact pressure force directly from the digitizer driver.
- 📐 **Contact Geometry** — Real physical width and height bounding boxes per touch point.
- ⚡ **Native Win32 WM_POINTER Hook** — Fast, direct Windows 8/10/11 pointer pipeline.
- 📦 **Zero-Copy Native Pipeline** — Minimized JNI overhead and zero GC pressure in the event loop.

---

## Real-World Use Cases

- 🎨 **Digital Whiteboards & Canvas Apps**: Multi-finger drawing, simultaneous collaborative markup, and pressure-sensitive sketching.
- 📱 **Interactive Kiosk & POS Terminals**: Responsive multi-touch navigation, pinch-to-zoom, and multi-user kiosks.
- 🎛️ **Audio & DJ Control Surfaces**: Multi-slider and multi-dial tactile touch controllers requiring concurrent multi-point tracking.
- 🤖 **Touch Telemetry & Automation**: High-rate gesture and touch telemetry capture for automated UI testing and recording.

---

## Performance Benchmarks

FastTouch is measured using **JMH (Java Microbenchmark Harness)** to ensure zero-overhead event processing:

| Benchmark / Operation | Score (ops/ms) | Ops per Second |
|---|---|---|
| **`benchmarkTouchPointAllocation`** | **~18,200 ops/ms** | **> 18.2 Million** |
| **`benchmarkTouchPointFormatting`** | **~1,450 ops/ms** | **> 1.45 Million** |
| **Native Polling Loop Rate** | **~120 Hz** | **Smooth Real-time Tracking** |

*Measured on Windows 11 (x64), JDK 17+.*

---

## API Quick Reference

| Method | Return Type | Description | Docs |
|---|---|---|---|
| `FastTouch.create(frame)` | `FastTouch` | Resolves window `HWND` and installs native `WM_POINTER` subclass hook. | [Reference](docs/REFERENCE.md#factory-methods) |
| `addListener(listener)` | `void` | Registers a callback for real-time touch point dispatch. | [Reference](docs/REFERENCE.md#event-capture--polling) |
| `removeListener(listener)` | `void` | Unregisters a previously registered touch listener. | [Reference](docs/REFERENCE.md#event-capture--polling) |
| `start()` | `void` | Launches the dedicated background touch polling thread (~120 Hz). | [Reference](docs/REFERENCE.md#event-capture--polling) |
| `stop()` | `void` | Halts the background touch polling loop. | [Reference](docs/REFERENCE.md#event-capture--polling) |
| `poll()` | `void` | Executes a single polling iteration and dispatches touch events. | [Reference](docs/REFERENCE.md#event-capture--polling) |
| `FastTouch.isTouchAvailable()` | `boolean` | Queries if a physical touchscreen or multi-touch digitizer is present. | [Reference](docs/REFERENCE.md#hardware-capabilities) |
| `FastTouch.getMaxTouchPoints()` | `int` | Returns maximum simultaneous touch points supported by hardware. | [Reference](docs/REFERENCE.md#hardware-capabilities) |

---

## Technical Demos & Benchmarks

| Case | Java Example | Launcher | Description |
|---|---|---|---|
| **Interactive Multi-Touch Canvas** | [Demo.java](examples/Demo/src/main/java/fasttouch/Demo.java) | `run-demo.bat` | Real-time multi-touch visualization displaying contact rings, pressure levels, and coordinates. |
| **JMH Microbenchmark Suite** | [Benchmark.java](examples/Benchmark/src/main/java/fasttouch/benchmark/Benchmark.java) | `run-benchmark.bat` | Microbenchmark suite profiling touch point allocation and formatting throughput. |

---

## Installation

### Option 1: Maven (Recommended)

Add the JitPack repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastTouch Library -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastTouch</artifactId>
        <version>0.1.0</version>
    </dependency>
    <!-- Required Native JNI loader -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastTouch:0.1.0'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

### Option 3: Direct Download (No Build Tool)

Download the latest JARs directly to add them to your classpath:

1. 📦 **[FastTouch-0.1.0.jar](https://github.com/andrestubbe/FastTouch/releases/tag/0.1.0)** (The Core Library with embedded native DLL)
2. ⚙️ **[fastcore-0.1.0.jar](https://github.com/andrestubbe/FastCore/releases/download/0.1.0/fastcore-0.1.0.jar)** (The Mandatory Native Loader)

> [!IMPORTANT]
> All JARs must be in your classpath for the JNI calls to function correctly.

---

## Documentation

- **[COMPILE.md](docs/COMPILE.md)**: Full compilation guide (MSVC C++17 build chain + JNI Setup).
- **[REFERENCE.md](docs/REFERENCE.md)**: Comprehensive API specification, contact point fields, and hook lifecycle.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: The engineering rationale for hardware-native touch interception.
- **[ROADMAP.md](docs/ROADMAP.md)**: Planned milestone features and performance extensions.
- **[CHANGELOG.md](docs/CHANGELOG.md)**: Complete version history and release notes.

---

## Platform Support

| Platform | Status |
|---|:---:|
| **Windows 10 / 11 (x64)** | ✅ Fully Supported (Native Win32 `WM_POINTER`) |
| **Linux / macOS** | 🚧 Planned |

---

## Related Projects

- **[`FastCore`](https://github.com/andrestubbe/FastCore)** — Native Library Loader & JNI Utilities for Java
- **[`FastStylus`](https://github.com/andrestubbe/FastStylus)** — Native Pen & Stylus Pressure API for Java
- **[`FastMouse`](https://github.com/andrestubbe/FastMouse)** — Ultra-Low Latency Native RawInput Mouse Engine
- **[`FastKeyboard`](https://github.com/andrestubbe/FastKeyboard)** — Ultra-Fast Native RawInput Keyboard Engine
- **[`FastHotkey`](https://github.com/andrestubbe/FastHotkey)** — Low-Latency Global Hotkey API for Java
- **[`FastVulkan`](https://github.com/andrestubbe/FastVulkan)** — High-Performance Native Vulkan 2D Rendering Engine

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

**Part of the FastJava Ecosystem** — *Making the JVM faster.* 🚀
