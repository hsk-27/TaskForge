param (
    [switch]$Clean
)

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " TaskForge Engine - Low-Resource Eco-Mode Launcher" -ForegroundColor Green
Write-Host " JVM Memory Limit: 256MB | Background Thread Priority: Low" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendJar = Join-Path $ScriptDir "backend\target\taskforge-backend-1.0.0.jar"

if ($Clean) {
    $DataDir = Join-Path $ScriptDir "data"
    if (Test-Path $DataDir) {
        Write-Host "[INFO] Purging local database files for pristine zero-job startup..." -ForegroundColor Yellow
        Remove-Item $DataDir -Recurse -Force
    }
}

if (-not (Test-Path $BackendJar)) {
    Write-Host "[INFO] Packaging backend JAR for optimal low-memory startup..." -ForegroundColor Yellow
    Set-Location (Join-Path $ScriptDir "backend")
    mvn clean package -DskipTests
    Set-Location $ScriptDir
}

Write-Host "[1/2] Starting TaskForge Backend with bounded heap (-Xms64m -Xmx256m)..." -ForegroundColor Cyan
Start-Process java -ArgumentList "-Xms64m", "-Xmx256m", "-XX:+UseG1GC", "-Dspring.profiles.active=local", "-Dtaskforge.engine.eco-mode=true", "-Dtaskforge.engine.worker-count=2", "-jar", "`"$BackendJar`"" -WindowStyle Minimized

Write-Host "[2/2] Starting TaskForge Frontend dev server..." -ForegroundColor Cyan
Set-Location (Join-Path $ScriptDir "frontend")
Start-Process npm.cmd -ArgumentList "run", "dev"

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " TaskForge is running in PC-Safe Eco-Mode!" -ForegroundColor Green
Write-Host " Backend API & Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host " Frontend Dashboard:    http://localhost:5173" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Cyan
