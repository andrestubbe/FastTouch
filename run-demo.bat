@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ⚡ Building FastTouch...
call mvn clean install -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo ❌ Build failed. & pause & exit /b %ERRORLEVEL% )

powershell -NoProfile -Command "Unblock-File -Path '%USERPROFILE%\.fastcore\native\fasttouch\*', '%~dp0src\main\resources\native\*', '%~dp0release\*', '%~dp0out\*' -ErrorAction SilentlyContinue" >nul 2>&1

echo 🛠  Compiling Demo...
cd examples\Demo
call mvn compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DincludeScope=runtime -q
if %ERRORLEVEL% NEQ 0 ( echo ❌ Compile failed. & pause & exit /b %ERRORLEVEL% )

echo 🚀 Running Demo...
set /p CP=<cp.txt
java --enable-native-access=ALL-UNNAMED "-Djava.library.path=%~dp0out;%~dp0release;%~dp0src\main\resources\native" -cp "target\classes;%CP%" fasttouch.Demo

cd ..\..
pause
