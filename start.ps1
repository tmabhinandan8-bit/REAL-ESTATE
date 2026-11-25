$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
$javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue

if (-not $javaCommand) {
    $jdkDirs = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium"),
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft"
    )

    foreach ($root in $jdkDirs) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem $root -Directory -Recurse -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "bin\java.exe" } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($candidate) {
            $javaCommand = Get-Item $candidate
            $env:JAVA_HOME = Split-Path (Split-Path $candidate -Parent) -Parent
            $env:Path = "$(Split-Path $candidate -Parent);$env:Path"
            break
        }
    }
}

if (-not $javaCommand) {
    Write-Error "Java 11 or newer was not found. Install Eclipse Temurin JDK 11 and try again."
    exit 1
}

$java = $javaCommand.Source
$javac = if ($javacCommand) { $javacCommand.Source } else { Join-Path (Split-Path $java -Parent) "javac.exe" }
if (-not (Test-Path $javac)) {
    Write-Error "The Java compiler (javac) was not found. Install a JDK, not only a JRE."
    exit 1
}

try {
    $existing = Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    if ($existing.StatusCode -ge 200 -and $existing.StatusCode -lt 400) {
        Write-Host "EstateEase is already running at http://localhost:8080" -ForegroundColor Green
        Start-Process "http://localhost:8080/"
        exit 0
    }
} catch {}

Write-Host "Starting EstateEase Real Estate Management System (Java + Supabase)..." -ForegroundColor Green
$java = Join-Path $jdkDir "bin\java.exe"
if (-not (Test-Path $java)) {
    Write-Error "Java 11 was not found at '$jdkDir'. Install JDK 11 and try again."
    exit 1
}

New-Item -ItemType Directory -Path "out" -Force | Out-Null
& $javac -encoding UTF-8 -d "out" "RealEstateBackend.java"
if ($LASTEXITCODE -ne 0) {
    Write-Error "The Java backend did not compile."
    exit $LASTEXITCODE
}

Write-Host "Starting EstateEase at http://localhost:8080/ ..."
& $java -cp "out" RealEstateBackend
