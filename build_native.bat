@echo off
cd /d "%~dp0"

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

cl /O2 /EHsc /LD /Fe:out\FastTouch.dll native\FastTouch.cpp /I"C:\Program Files\Java\jdk-25\include" /I"C:\Program Files\Java\jdk-25\include\win32" user32.lib

echo.
echo Build complete!
pause
