# Building FastTouch from Source

## Prerequisites

- **JDK 17+** — [Download](https://adoptium.net/)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **Visual Studio 2019/2022** — Community, Professional, or BuildTools with C++ Desktop development

## Quick Build

```bash
# 1. Build native DLL first (Windows)
compile.bat

# 2. Build JAR & install locally
mvn clean install -DskipTests
```

## Build Commands

| Command | Purpose |
|---|---|
| `compile.bat` | Compiles native C++ DLL (`FastTouch.dll`) using MSVC `cl.exe`. |
| `mvn clean compile` | Compiles Java sources only. |
| `mvn clean package` | Packages release JAR with compiled native DLL bundled. |
| `run-demo.bat` | Compiles and launches the interactive visual multi-touch demo. |
| `run-benchmark.bat` | Runs the JMH microbenchmark suite. |

## Native DLL Build

The `compile.bat` script:
- Auto-detects Visual Studio via `vswhere.exe`.
- Auto-detects `JAVA_HOME`.
- Compiles `native\FastTouch.cpp` with `/O2 /LD /EHsc`.
- Links against `user32.lib` and `gdi32.lib`.
- Copies `FastTouch.dll` into:
  - `out\`
  - `release\`
  - `src\main\resources\native\`
  - `%USERPROFILE%\.fastcore\native\FastTouch\` (FastCore loader cache)

## Troubleshooting

**"Cannot find DLL" / UnsatisfiedLinkError**:
1. Run `compile.bat` to compile `out\FastTouch.dll`.
2. Ensure `FastTouch.dll` is present in `src\main\resources\native\` or passed via `-Djava.library.path=out`.
