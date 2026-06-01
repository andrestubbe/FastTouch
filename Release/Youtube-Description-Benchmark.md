**15.2 Million Operations/Sec in Java! (JMH Benchmark)** 🏎️💨
In this video, we run the official JMH (Java Microbenchmark Harness) test for `FastTouch`, the new native Windows touchscreen library for the JVM.

We are stress-testing the `FastGesture` engine — the component responsible for calculating complex multi-touch physics like Panning, Pinch-to-Zoom, and Rotation directly from raw `WM_POINTER` hardware coordinates. 

🔥 **The Results:**
By utilizing a strictly zero-allocation, lock-free architecture with native primitive arrays, the engine achieves a staggering **15,229,163 operations per millisecond** (ops/ms) on a single thread. 
Given that a high-end touch digitizer polls at 120Hz-240Hz, the computational overhead of this math engine in Java is functionally unmeasurable. True Native-First Performance!

📦 **Check out the code & run the benchmark yourself:**
GitHub: https://github.com/andrestubbe/FastTouch
(Just run the `run-benchmark.bat` in the root folder)

---
🔗 **The FastJava Ecosystem**
FastTouch is part of the FastJava ecosystem — building the fastest native integrations for the JVM.
Zero bloat. Zero garbage. Maximum speed.
Discover more:
- FastAnimation (High-performance timeline engine)
- FastCore (Native Library Loader)
- FastTheme (Native Window Styling)

#Java #JMH #Benchmark #Performance #Optimization #MultiTouch #FastJava #SoftwareEngineering #Coding #OpenSource
