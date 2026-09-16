@echo off
setlocal enabledelayedexpansion
set PROJECT_NAME=FastTouch

echo ===========================================
echo FastTouch Native Builder (FastJava Standard)
echo ===========================================

:: Auto-detect Visual Studio via vswhere
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "!VSWHERE!" (
    for /f "usebackq tokens=*" %%i in (`"!VSWHERE!" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        set "VS_PATH=%%i"
    )
)

if not defined VS_PATH (
    if exist "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" (
        set "VS_PATH=C:\Program Files\Microsoft Visual Studio\18\Community"
    ) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat" (
        set "VS_PATH=C:\Program Files\Microsoft Visual Studio\2022\Community"
    ) else if exist "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" (
        set "VS_PATH=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
    )
)

if not defined VS_PATH (
    echo ERROR: Visual Studio not found!
    exit /b 1
)

echo Found Visual Studio at: !VS_PATH!

:: Detect JAVA_HOME
if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-21.0.12.1" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
    ) else if exist "C:\Program Files\Java\jdk-25" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-25"
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    )
)

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME not found!
    exit /b 1
)

echo Using JAVA_HOME: !JAVA_HOME!

call "!VS_PATH!\VC\Auxiliary\Build\vcvars64.bat"

if not exist out mkdir out
if not exist release mkdir release
if not exist src\main\resources\native mkdir src\main\resources\native
set "FASTCORE_DIR=%USERPROFILE%\.fastcore\native\%PROJECT_NAME%"
if not exist "!FASTCORE_DIR!" mkdir "!FASTCORE_DIR!"

echo.
echo Compiling C++ Native Library...
cl.exe /LD /O2 /EHsc /Fe:out\%PROJECT_NAME%.dll native\FastTouch.cpp /I"!JAVA_HOME!\include" /I"!JAVA_HOME!\include\win32" /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib

if %ERRORLEVEL% EQU 0 (
    copy /Y out\%PROJECT_NAME%.dll release\%PROJECT_NAME%.dll >nul
    copy /Y out\%PROJECT_NAME%.dll src\main\resources\native\%PROJECT_NAME%.dll >nul
    copy /Y out\%PROJECT_NAME%.dll "!FASTCORE_DIR!\%PROJECT_NAME%.dll" >nul
    powershell -NoProfile -Command "Unblock-File -Path '!FASTCORE_DIR!\%PROJECT_NAME%.dll', 'release\%PROJECT_NAME%.dll', 'out\%PROJECT_NAME%.dll', 'src\main\resources\native\%PROJECT_NAME%.dll' -ErrorAction SilentlyContinue" >nul 2>&1
    echo.
    echo ===========================================
    echo [SUCCESS] FastTouch native DLL built!
    echo Copied to out\, release\, resources\native\, and .fastcore
    echo ===========================================
) else (
    echo.
    echo !!!!!!!!! BUILD FAILED !!!!!!!!!
    exit /b 1
)
