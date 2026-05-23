@echo off
set DEMO_DIR=%~dp0
set CLASSES=%DEMO_DIR%target\classes
set LIBS=%DEMO_DIR%target\lib\*

java -cp "%CLASSES%;%LIBS%" fasttheme.WindowDemo
