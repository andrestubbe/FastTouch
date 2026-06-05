@echo off
chcp 65001 >nul

echo ðŸš€ Running Hero Demo...
cd examples\Demo
call mvn -q compile exec:java -Dexec.mainClass=fasttouch.TouchDemo
cd ..\..
pause
