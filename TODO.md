# FastTouch — TODO

## ✅ Completed

*Repository structure created*

---

## 🚧 In Progress

### Core Touch Input
- [ ] Native Windows touch message handling (WM_TOUCH / WM_POINTER)
- [ ] Multi-touch point tracking
- [ ] Pressure sensitivity integration
- [ ] Touch contact size (width/height)

---

## ⏳ Pending

### Java API
- [ ] FastTouch.java class implementation
- [ ] TouchListener interface
- [ ] TouchPoint data structure
- [ ] Window handle integration (FindWindow)

### Native Implementation (C++)
- [ ] RegisterTouchWindow() setup
- [ ] WM_TOUCH message processing
- [ ] WM_POINTER message processing (Windows 8+)
- [ ] Touch point buffer management
- [ ] JNI method implementations

### Polling & Events
- [ ] High-frequency polling thread
- [ ] Event callback system
- [ ] Touch state tracking (DOWN/MOVE/UP)
- [ ] Touch ID lifecycle management

### Features
- [ ] Gesture recognition (pinch, swipe, rotate)
- [ ] Palm rejection
- [ ] Hover detection (if supported)
- [ ] Touch visualization debug overlay

### Demos
- [ ] Basic touch tracking demo
- [ ] Multi-touch paint demo
- [ ] Pressure-sensitive drawing demo
- [ ] Gesture recognition demo

### Testing
- [ ] Single touch test
- [ ] Multi-touch stress test (10 points)
- [ ] Pressure accuracy test
- [ ] Latency benchmark vs AWT

### Documentation
- [ ] API JavaDoc
- [ ] Native implementation notes
- [ ] Touch hardware compatibility list
- [ ] Troubleshooting guide

---

## 🎯 Known Limitations

- Windows only (uses Win32 API)
- Requires touchscreen hardware
- Some tablets may report pressure as binary (0/1)

---

## 💡 Future Ideas

- Linux support (evdev)
- macOS support (NSTouch)
- Pen/Stylus support (separate from touch)
- Haptic feedback control
