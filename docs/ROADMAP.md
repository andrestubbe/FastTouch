# FastTouch Roadmap 🗺️

**Vision:** To provide the fastest possible native primitives for touchscreen and multi-touch digitizers by aggressively bypassing bottlenecks in standard Java.

## 🟢 v0.1.0: Initial Release (Current)
- [x] **Core Native Engine**: Win32 `WM_POINTER` JNI implementation.
- [x] **Multi-Touch Tracking**: 10+ simultaneous finger contacts with unique IDs.
- [x] **Pressure & Sizing**: Hardware contact force and bounding box pixel dimensions.
- [x] **Blueprint Standards**: README, Reference, and Philosophy integration.

## 🟡 v0.2.0: Optimization Phase
- [ ] **Direct Native Event Dispatch**: Replace polling thread with direct C++ window subclass callback invocation.
- [ ] **Contact Filtering**: Palm rejection heuristics and confidence threshold gating.
- [ ] **Zero-Allocation Ring Buffer**: Off-heap circular buffer for high-frequency gesture streams.

## 🟠 v0.5.0: Platform & Logic Expansion
- [ ] **Stylus / Pen Parity**: Integration with barrel button states and eraser tip detection.
- [ ] **Linux (libinput / evdev) Port**: Touch parity for Linux desktop and embedded kiosks.

## 🔴 v1.0.0: Production Hardening
- [ ] **Full Stability Audit**: Long-run stress testing on diverse Windows digitizer hardware.
- [ ] **Enterprise Kiosk Support**: Multi-monitor multi-touch display mapping.

---
**Focus:** Performance is our USP. We optimize where Java stops.
