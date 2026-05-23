@echo off
chcp 65001 >nul
echo ==========================================
echo FastTouch WindowDemo - Touch Console
echo ==========================================
echo.

set DEMO_DIR=%~dp0
set LIB_DIR=%DEMO_DIR%target\lib
set CLASSES_DIR=%DEMO_DIR%target\classes

REM Check if classes exist
if not exist "%CLASSES_DIR%\fasttheme\WindowDemo.class" (
    echo [ERROR] WindowDemo.class not found! Run: mvn clean compile
    pause
    exit /b 1
)

REM Build classpath
set CLASSPATH=%CLASSES_DIR%
for %%f in (%LIB_DIR%\*.jar) do (
    set CLASSPATH=%CLASSPATH%;%%f
)

REM Add DLL directory to PATH
set PATH=%LIB_DIR%;%PATH%

echo Classpath: %CLASSPATH%
echo.
echo Starting WindowDemo with Touch Events...
echo Touch the screen - events will appear here!
echo Press ESC in the window to exit
echo ==========================================
echo.

java -cp "%CLASSPATH%" fasttheme.WindowDemo

pause
