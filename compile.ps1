#
# FastTouch Builder
# Builds Java classes and native DLL
#

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$src = "$root\src"
$out = "$root\out"
$native = "$root\native"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "FastTouch Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check Java
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaHome = "C:\Program Files\Java\jdk-25"
}

Write-Host "`nJava: $javaHome" -ForegroundColor Gray

# Clean/Create out directory
if (Test-Path $out) {
    Remove-Item "$out\*" -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $out | Out-Null

# Compile Java
Write-Host "`n[1/2] Java kompilieren..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path $src -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
if ($javaFiles.Count -gt 0) {
    & "$javaHome\bin\javac.exe" -d $out -cp $out $javaFiles 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK" -ForegroundColor Green
    } else {
        Write-Host "FEHLER: Java Build fehlgeschlagen!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Keine Java-Dateien gefunden" -ForegroundColor Yellow
}

# Build Native DLL
Write-Host "`n[2/2] Native DLL (FastTouch)..." -ForegroundColor Yellow

# Find VS
$vsPath = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
if (-not (Test-Path $vsPath)) {
    $vsPath = "C:\Program Files\Microsoft Visual Studio\2022\Community"
}
if (-not (Test-Path $vsPath)) {
    $vsPath = "C:\Program Files\Microsoft Visual Studio\2022\Professional"
}

if (-not (Test-Path $vsPath)) {
    Write-Host "FEHLER: Visual Studio 2022 nicht gefunden!" -ForegroundColor Red
    exit 1
}

Write-Host "VS: $vsPath" -ForegroundColor Gray

# Setup VS environment
Import-Module "$vsPath\Common7\Tools\Microsoft.VisualStudio.DevShell.dll"
Enter-VsDevShell -VsInstallPath $vsPath -SkipAutomaticLocation

# Compile C++
$cppFile = "$native\FastTouch.cpp"
if (Test-Path $cppFile) {
    & cl.exe /O2 /EHsc /LD /Fe:"$out\FastTouch.dll" `
        $cppFile `
        /I"$javaHome\include" `
        /I"$javaHome\include\win32" `
        user32.lib gdi32.lib 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n========================================" -ForegroundColor Green
        Write-Host "BUILD ERFOLGREICH!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "`nStarten mit:" -ForegroundColor Cyan
        Write-Host "  cd $out" -ForegroundColor White
        Write-Host "  java -cp . -Djava.library.path=. demo.TouchDemo" -ForegroundColor White
    } else {
        Write-Host "`nFEHLER: Native Build fehlgeschlagen!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "WARNUNG: $cppFile nicht gefunden" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BUILD ERFOLGREICH (nur Java)" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
}

Write-Host "`nFastTouch bereit!" -ForegroundColor Cyan
