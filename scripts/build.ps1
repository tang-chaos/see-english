$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (!(Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    if (!(Test-Path -LiteralPath $sdkPath)) { throw 'Install Android SDK or set ANDROID_HOME.' }
    $sdkLine = 'sdk.dir=' + $sdkPath.Replace('\','/').Replace(':','\:')
    [System.IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), $sdkLine + "`n", [System.Text.UTF8Encoding]::new($false))
}
& .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -PtargetAbi=arm64-v8a --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed. See Gradle output.' }
$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
$artifactPath = Join-Path $artifactDir 'see-english-0.8.5-arm64-debug.apk'
Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $artifactPath
$hash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($artifactPath + '.sha256', $hash + "`n", [System.Text.UTF8Encoding]::new($false))
$screenshots = Join-Path $projectRoot 'app\build\verification'
if (Test-Path -LiteralPath $screenshots) {
    $screenshotsDestination = Join-Path $artifactDir 'screenshots/0.8.5'
    New-Item -ItemType Directory -Path $screenshotsDestination -Force | Out-Null
    Get-ChildItem -LiteralPath $screenshots -Filter '*.png' | Where-Object {
        $_.Name -in @('20-beautified-camera-ui-fixture.png','21-beautified-favorites-ui-fixture.png','22-beautified-history-ui-fixture.png','23-beautified-permission.png','24-beautified-model-panel-ui-fixture.png','26-word-card-without-example.png','04-large-vocabulary.png','25-beautified-portrait-camera-ui-fixture.png')
    } | Copy-Item -Destination $screenshotsDestination -Force
}
Write-Host ('APK: ' + $artifactPath)
Write-Host ('SHA256: ' + $hash)
Write-Host 'To rebuild the complete offline ZIP after verification, run: python scripts/package-offline.py'
