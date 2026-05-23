@echo off
setlocal enabledelayedexpansion

echo =====================================================
echo FastTouch Native Compiler
echo =====================================================
echo.

REM Hardcoded JDK path - adjust if needed
set "JDK_PATH=C:\Program Files\Java\jdk-25"

REM Verify JDK exists
if not exist "%JDK_PATH%\include\jni.h" (
    echo ERROR: JDK not found at %JDK_PATH%
    echo.
    echo Please install JDK 17+ to C:\Program Files\Java\jdk-XX
    echo or edit this batch file and set JDK_PATH to your JDK location.
    pause
    exit /b 1
)

echo Using JDK: %JDK_PATH%

REM Auto-detect Visual Studio
set "VS_PATH="
for %%p in (
    "C:\Program Files\Microsoft Visual Studio\2022\Community"
    "C:\Program Files\Microsoft Visual Studio\2022\Enterprise"
    "C:\Program Files\Microsoft Visual Studio\2022\Professional"
    "C:\Program Files\Microsoft Visual Studio\2022\BuildTools"
    "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
) do (
    if exist "%%~p\VC\Auxiliary\Build\vcvars64.bat" (
        set "VS_PATH=%%~p"
        goto :found_vs
    )
)

:found_vs
if not defined VS_PATH (
    echo ERROR: Visual Studio 2022 not found.
    pause
    exit /b 1
)

echo Using Visual Studio: %VS_PATH%

REM Setup environment
call "%VS_PATH%\VC\Auxiliary\Build\vcvars64.bat"

REM Create build directory
if not exist build mkdir build

echo.
echo Compiling FastTouch Native DLL...
echo =====================================================

cl /LD /Fe:build\fasttouch.dll ^
    native\FastTouch.cpp ^
    user32.lib ^
    /I"%JDK_PATH%\include" ^
    /I"%JDK_PATH%\include\win32" ^
    /EHsc /std:c++17 /O2 /W3 ^
    /link /DEF:native\FastTouch.def

if %ERRORLEVEL% neq 0 (
    echo.
    echo =====================================================
    echo COMPILATION FAILED
    echo =====================================================
    pause
    exit /b 1
)

echo.
echo =====================================================
echo BUILD SUCCESSFUL: build\fasttouch.dll
echo =====================================================
echo.
pause
