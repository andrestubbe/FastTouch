@echo off
chcp 65001 >nul

echo ⚡ Building Main Project...
call mvn -q install -DskipTests
if %ERRORLEVEL% NEQ 0 ( pause & exit /b )
echo 🚀 Running Hero Demo...
cd examples\Demo
call mvn -q compile exec:java -Dexec.mainClass=fasttouch.TouchDemo
cd ..\..
pause
