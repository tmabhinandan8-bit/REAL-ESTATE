@echo off
setlocal
cd /d "%~dp0"

curl -s --max-time 2 http://localhost:8080/health >nul 2>&1
if not errorlevel 1 (
    echo EstateEase is already running at http://localhost:8080
    start "" http://localhost:8080/
    exit /b 0
)

where java.exe >nul 2>&1
if errorlevel 1 (
    echo Java was not found in PATH.
    echo Install Eclipse Temurin JDK 11 or newer, then retry.
    pause
    exit /b 1
)

where javac.exe >nul 2>&1
if errorlevel 1 (
    echo javac was not found.
    echo Install a JDK, not only a JRE.
    pause
    exit /b 1
)

echo Starting EstateEase Real Estate Management System (Java + Supabase)...
if not exist out mkdir out
javac -encoding UTF-8 -d out RealEstateBackend.java
if errorlevel 1 (
    echo The Java backend did not compile.
    pause
    exit /b 1
)

start "EstateEase Backend Server" java -cp out RealEstateBackend
timeout /t 3 /nobreak >nul
start "" http://localhost:8080/
