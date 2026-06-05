@echo off

echo [FastTouch] Running Demo (via JitPack)...
cd examples\Demo
call mvn compile exec:java -Dexec.mainClass=fasttouch.TouchDemo
cd ..\..
pause
