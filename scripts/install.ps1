$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$apkPath = Join-Path $projectRoot 'artifacts\see-english-0.8.5-arm64-debug.apk'
if (!(Test-Path -LiteralPath $apkPath)) { throw 'Run build.cmd first.' }
if (!(Test-Path -LiteralPath $adbPath)) { throw 'Install Android SDK platform-tools or set ANDROID_HOME.' }
$devices = @(& $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) {
    & $adbPath devices -l
    throw 'Connect exactly one Android phone, enable USB debugging and approve this computer on the phone.'
}
$serial = ($devices[0] -split '\s+')[0]
$abis = (& $adbPath -s $serial shell getprop ro.product.cpu.abilist | Out-String).Trim()
$arm64Path = Join-Path $projectRoot 'artifacts\see-english-0.8.5-arm64-debug.apk'
if ($abis -match 'arm64-v8a' -and (Test-Path -LiteralPath $arm64Path)) { $apkPath = $arm64Path }
& $adbPath -s $serial install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adbPath -s $serial shell am start -n com.codex.englishcamera/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Application launch failed.' }
Write-Host 'APK installed. For the full offline model setup, use install-complete-offline.cmd or the offline ZIP installer.'
