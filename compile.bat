@echo off
@echo FastTouch Compiler
echo.

REM Java kompilieren
javac -d out src\fasttouch\*.java src\demo\*.java
if errorlevel 1 (
    echo Java compilation failed!
    exit /b 1
)

REM Native DLL kompilieren (requires VS2022)
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cl /O2 /EHsc /LD /Fe:out\FastTouch.dll native\FastTouch.cpp /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" user32.lib

echo.
echo Done! Run with:
echo   cd out
echo   java -cp . -Djava.library.path=. demo.TouchDemo
pause
