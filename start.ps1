$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# 1. Resolve java.exe and javac.exe
$java = $null
$javac = $null

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
$javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue

if ($javaCommand) { $java = $javaCommand.Source }
if ($javacCommand) { $javac = $javacCommand.Source }

# If not found in system PATH, search standard JDK locations
if (-not $java -or -not $javac) {
    $searchRoots = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium"),
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft"
    )
    if ($env:JAVA_HOME) { $searchRoots = @($env:JAVA_HOME) + $searchRoots }

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        $foundJava = Get-ChildItem $root -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        $foundJavac = Get-ChildItem $root -Recurse -Filter "javac.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($foundJava -and -not $java) { $java = $foundJava.FullName }
        if ($foundJavac -and -not $javac) { $javac = $foundJavac.FullName }
        if ($java -and $javac) { break }
    }
}

if (-not $java -or -not (Test-Path $java)) {
    Write-Error "Java 11 or newer was not found. Please install Eclipse Temurin JDK 11 and try again."
    exit 1
}

if (-not $javac -or -not (Test-Path $javac)) {
    $siblingJavac = Join-Path (Split-Path $java -Parent) "javac.exe"
    if (Test-Path $siblingJavac) {
        $javac = $siblingJavac
    } else {
        Write-Error "The Java compiler (javac.exe) was not found. Please install a full JDK (not just JRE)."
        exit 1
    }
}

# 2. Check if already running on port 8080
try {
    $existing = Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    if ($existing.StatusCode -ge 200 -and $existing.StatusCode -lt 400) {
        Write-Host "EstateEase is already running at http://localhost:8080" -ForegroundColor Green
        Start-Process "http://localhost:8080/"
        exit 0
    }
} catch {}

# 3. Compile RealEstateBackend.java
Write-Host "Starting EstateEase Real Estate Management System (Java Backend - Standalone Data Store)..." -ForegroundColor Green
New-Item -ItemType Directory -Path "out" -Force | Out-Null
& $javac -encoding UTF-8 -d "out" "RealEstateBackend.java"
if ($LASTEXITCODE -ne 0) {
    Write-Error "The Java backend failed to compile."
    exit $LASTEXITCODE
}

# 4. Asynchronously launch browser once server is responsive
Start-Job -ScriptBlock {
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 600
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop
            if ($r.StatusCode -eq 200) {
                Start-Process "http://localhost:8080/"
                break
            }
        } catch {}
    }
} | Out-Null

Write-Host ">>> Backend Server listening at http://localhost:8080 ..." -ForegroundColor Cyan
Write-Host ">>> Press Ctrl+C in this terminal to stop the server.`n" -ForegroundColor DarkGray
& $java -cp "out" RealEstateBackend
