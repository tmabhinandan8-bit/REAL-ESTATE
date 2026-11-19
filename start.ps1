$jdkDir = Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium\jdk-11.0.33+10"
if (Test-Path (Join-Path $jdkDir "bin\java.exe")) {
    $env:JAVA_HOME = $jdkDir
    $env:Path = "$jdkDir\bin;$env:Path"
}

try {
    $existing = Invoke-WebRequest -Uri "http://localhost:8080/" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    if ($existing.StatusCode -ge 200 -and $existing.StatusCode -lt 400) {
        Write-Host "Real Estate frontend is already running at http://localhost:8080" -ForegroundColor Green
        Start-Process "http://localhost:8080/"
        exit 0
    }
} catch {}

Write-Host "Starting Real Estate Management System (Java + Supabase)..." -ForegroundColor Green
$java = Join-Path $jdkDir "bin\java.exe"
if (-not (Test-Path $java)) {
    Write-Error "Java 11 was not found at '$jdkDir'. Install JDK 11 and try again."
    exit 1
}
& $java RealEstateBackend.java
