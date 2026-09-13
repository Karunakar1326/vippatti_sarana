$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Vippatti Sarana - Build & Run" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check ADB device
Write-Host "[1/4] Checking Android device..." -ForegroundColor Yellow

$devices = adb devices | Select-String "`tdevice"

if (-not $devices) {
    Write-Host ""
    Write-Host "ERROR: No Android device detected." -ForegroundColor Red
    Write-Host "Connect your phone and make sure USB debugging is enabled."
    exit 1
}

Write-Host "Device detected." -ForegroundColor Green
Write-Host ""

# Build APK
Write-Host "[2/4] Building APK..." -ForegroundColor Yellow

.\gradlew.bat assembleDebug --no-configuration-cache --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Build failed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Build successful." -ForegroundColor Green
Write-Host ""

# Install APK
Write-Host "[3/4] Installing APK..." -ForegroundColor Yellow

$apk = ".\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    Write-Host "ERROR: APK not found at $apk" -ForegroundColor Red
    exit 1
}

adb install -r $apk

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: APK installation failed." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "APK installed successfully." -ForegroundColor Green
Write-Host ""

# Launch app
Write-Host "[4/4] Launching Vippatti Sarana..." -ForegroundColor Yellow

adb shell monkey -p com.aistudio.vippattisarana.gaqgel 1

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   App is running on your phone!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""