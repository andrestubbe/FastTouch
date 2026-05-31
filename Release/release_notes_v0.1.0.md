# FastTouch v0.1.0 — Native Touchscreen Input [ALPHA]

## 🚀 What's New
- **Initial Native Implementation** — Direct `WM_POINTER` access for zero-latency Multi-Touch.
- **FastCore Integration** — Unified JNI loader.
- **Native DLL bundled** — `fasttouch.dll` now included in the JAR.
- **FastGesture Engine** — Added utility for high-level mathematical operations (Panning, Pinch-to-Zoom, Physics Rotation) directly from raw touches.
- **Zero Allocation** — Native arrays in C++ without garbage collection overhead.
- Maven/JitPack support with fully standardized FastJava blueprint.

## 📦 Installation

### Maven (JitPack)
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fasttouch</artifactId>
        <version>v0.1.0</version>
    </dependency>
    <!-- Required Native JNI loader -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fastcore</artifactId>
        <version>v0.1.0</version>
    </dependency>
</dependencies>
```

### Gradle (JitPack)
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { 
    implementation 'com.github.andrestubbe:fasttouch:v0.1.0' 
    implementation 'com.github.andrestubbe:fastcore:v0.1.0'
}
```

### Direct Download
- `fasttouch-v0.1.0.jar` — Main library with DLL
- `fastcore-v0.1.0.jar` — JNI loader (required)
→ [Download from FastCore releases](https://github.com/andrestubbe/FastCore/releases)

## ⚡ Quick Start
```java
import fasttouch.FastTouch;
import javax.swing.JFrame;

JFrame frame = new JFrame("FastTouch Demo");
frame.setSize(800, 600);
frame.setVisible(true);

// Initialize native touch input
FastTouch touch = FastTouch.create(frame);

// Add touch listener
touch.addListener(point -> {
    System.out.println("Touch " + point.id + " at (" + point.x + "," + point.y + ")" +
                       " pressure=" + point.pressure);
});

// Start processing
touch.start();
```

## ✨ Key Features
- **Multi-Touch** — Track 10+ fingers simultaneously.
- **Pressure Sensitivity** — 0-255 pressure levels directly from capacitive screens.
- **Contact Size** — Width/height of the finger contact area in pixels.
- **Low Latency** — Hooks directly into the `WM_POINTER` API before AWT can see it.
- **Event-driven** — Zero-CPU polling, purely callback based.
- **FastGesture** — Mathematical tracking of pinches and rotation physics.

## 📁 Assets
- `fasttouch-v0.1.0.jar`
- `Source code (zip)`

---

Part of the **FastJava Ecosystem** — Making the JVM faster.
- https://github.com/andrestubbe/FastTouch
