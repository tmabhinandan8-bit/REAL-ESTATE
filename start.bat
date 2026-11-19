@echo off
setlocal
set "JDK_DIR=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-11.0.33+10"
if exist "%JDK_DIR%\bin\java.exe" (
    set "JAVA_HOME=%JDK_DIR%"
    set "PATH=%JDK_DIR%\bin;%PATH%"
)

curl -s --max-time 2 http://localhost:8080/ >nul 2>&1
if not errorlevel 1 (
    echo Real Estate frontend is already running at http://localhost:8080
    start "" http://localhost:8080/
    exit /b 0
)

echo Starting Real Estate Management System (Java + Supabase)...
"%JDK_DIR%\bin\java.exe" RealEstateBackend.java
if errorlevel 1 pause
