# FastTouch — Native touchscreen input for Java

**⚡ Ultra-fast native touchscreen input for Java — Multi-touch, pressure, and gestures impossible in pure Java**

[![Release](https://img.shields.io/badge/release-v1.1.0-blue.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-available-brightgreen.svg)](https://jitpack.io/#andrestubbe/FastTouch)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

![FastTouch Multi-Touch Demo](screenshot.png)

> **Native multi-touch touchscreen input** via Windows WM_POINTER API. Powered by FastCore.

FastTouch provides **hardware-level touchscreen access** for Java applications — something impossible with standard AWT/Swing. Get raw touch data including:
- **Multi-touch** — Track 10+ fingers simultaneously  
- **Pressure sensitivity** — Variable touch force (0-255)
- **Contact size** — Touch width/height in pixels
- **Low latency** — Native Windows API, no JVM event queue delays

**Java CANNOT do this.** AWT only provides mouse emulation for touch. FastTouch gives you the real thing.

---

## 📦 Why FastTouch?

| Feature | Java AWT/Swing | FastTouch (JNI) |
|---------|---------------|-----------------|
| Multi-Touch | ❌ No (mouse emulation only) | ✅ 10+ simultaneous points |
| Pressure | ❌ No | ✅ 0-255 pressure levels |
| Contact Size | ❌ No | ✅ Width/Height in pixels |
| Raw Touch Events | ❌ No (synthesized mouse) | ✅ Native WM_TOUCH/WM_POINTER |
| Latency | High (event queue) | **Native speed** |

---

## 🚀 Quick Start

```java
import fasttouch.FastTouch;
import javax.swing.JFrame;

public class TouchDemo {
    public static void main(String[] args) {
        JFrame frame = new JFrame("FastTouch Demo");
        frame.setSize(800, 600);
        frame.setVisible(true);
        
        // Initialize native touch input
        FastTouch touch = FastTouch.create(frame);
        
        // Add touch listener
        touch.addListener(point -> {
            System.out.println("Touch " + point.id + 
                " at (" + point.x + "," + point.y + ")" +
                " pressure=" + point.pressure +
                " state=" + point.state);
        });
        
        // Start polling
        touch.start();
        
        // Your app runs here...
    }
}
```

---

## 📦 Installation

### Maven (JitPack)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.andrestubbe</groupId>
    <artifactId>fasttouch</artifactId>
    <version>v1.1.0</version>
</dependency>
```

FastCore is automatically included as a transitive dependency.

### Gradle (JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.andrestubbe:fasttouch:v1.1.0'
}
```

### Direct Download

- **fasttouch-v1.1.0.jar** — Main library with DLL
- **fastcore-v1.0.0.jar** — [JNI loader](https://github.com/andrestubbe/FastCore/releases)

```bash
java -cp "fasttouch-v1.1.0.jar;fastcore-v1.0.0.jar" YourApp
```

### Build from Source

See [COMPILE.md](COMPILE.md) for detailed build instructions.

---

## 🎯 API Reference

### Core Methods

| Method | Description | Status |
|--------|-------------|--------|
| `FastTouch.create(window)` | Initialize touch for window | ✅ Working |
| `addListener(listener)` | Add touch event callback | ✅ Working |
| `start()` | Begin touch polling | ✅ Working |
| `stop()` | Stop touch polling | ✅ Working |
| `isTouchAvailable()` | Check if touchscreen present | ✅ Working |
| `getMaxTouchPoints()` | Get max simultaneous touches | ✅ Working |

### TouchPoint Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Touch ID (tracking) |
| `x, y` | int | Screen coordinates |
| `pressure` | int | 0-255 pressure level |
| `width, height` | int | Contact size in pixels |
| `state` | State | DOWN / MOVE / UP |
| `timestamp` | long | Event time in ms |

---

## 🏗️ Build from Source

### Prerequisites
- Windows 10/11 with Touchscreen
- Java JDK 17+
- Visual Studio 2022 (C++ workload)

### Build
```batch
compile.bat
mvn clean package
```

See [COMPILE.md](COMPILE.md) for detailed instructions.

---

## 📄 License

MIT License — See [LICENSE](LICENSE) for details.

---

## Project Structure

```
fasttouch/
├── .github/workflows/          # CI/CD
├── examples/00-basic-usage/     # Demo project
│   ├── pom.xml
│   └── src/main/java/fasttouch/TouchDemo.java
├── native/
│   ├── FastTouch.cpp          # Native implementation
│   ├── FastTouch.h            # Header file
│   └── FastTouch.def          # JNI exports (REQUIRED)
├── src/main/java/fasttouch/   # Library source
│   └── FastTouch.java
├── compile.bat                # Native build script
├── COMPILE.md                 # Build instructions
├── pom.xml                    # Maven config
└── README.md                  # This file
```

---

**FastTouch** — *Part of the FastJava Ecosystem*  
- [FastCore](https://github.com/andrestubbe/FastCore) — JNI loader
- More at [github.com/andrestubbe](https://github.com/andrestubbe)
