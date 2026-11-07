param([string]$Serial,[string]$BundleRoot=$PSScriptRoot,[string]$ModelPath,[string]$ManifestPath,[string]$AdbPath)
$ErrorActionPreference='Stop'
if(!$ManifestPath){$ManifestPath=Join-Path $bundleRoot 'bundle-manifest.json'}
$manifest=Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$apk=Join-Path $bundleRoot $manifest.apk.file
$model=if($ModelPath){$ModelPath}else{Join-Path $bundleRoot 'model'}
$adbCommand=Get-Command adb -ErrorAction SilentlyContinue
$adb=if($AdbPath){$AdbPath}elseif($adbCommand){$adbCommand.Source}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'}
if(!(Test-Path -LiteralPath $adb)){throw 'Android platform-tools (adb) is required for USB installation. Alternatively, install the APK on the phone and import the model folder inside the app.'}
$devices=@(& $adb devices | Select-Object -Skip 1 | Where-Object {$_ -match '^\S+\s+device$'} | ForEach-Object {($_ -split '\s+')[0]})
if(!$Serial){if($devices.Count -ne 1){throw 'Connect one phone, enable USB debugging, and allow this computer on the phone.'};$Serial=$devices[0]}
if($Serial -notin $devices){throw 'The selected phone is not connected and authorized.'}
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    & $adb -s $Serial @Arguments
    if($LASTEXITCODE -ne 0){throw "ADB failed: $($Arguments -join ' ')"}
}
Write-Host 'Checking the APK and model files...'
if((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant() -ne $manifest.apk.sha256){throw 'APK hash mismatch. Extract the full offline bundle again.'}
foreach($part in $manifest.model.files){
    $file=Join-Path $model $part.file
    if(!(Test-Path $file) -or (Get-Item $file).Length -ne $part.bytes){throw "Missing or incomplete model file: $($part.file)"}
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant() -ne $part.sha256){throw "Model hash mismatch: $($part.file)"}
}
$disk=@(Invoke-Adb @('shell','df','-k','/data'));$fields=($disk[-1].Trim() -split '\s+');$freeKiB=0L
if($fields.Length -lt 4 -or ![long]::TryParse($fields[3],[ref]$freeKiB)){throw 'Cannot determine available phone storage.'}
if($freeKiB -lt 12GB/1KB){throw 'Keep at least 12 GiB free on the phone for this experiment.'}
Write-Host 'Installing/updating See English. Existing vocabulary is retained.'
Invoke-Adb @('install','-r',$apk)
$remoteRoot='/sdcard/Android/data/com.codex.englishcamera/files/models'
$remoteModel="$remoteRoot/Qwen3-VL-8B-Instruct-MNN"
Invoke-Adb @('shell','am','force-stop','com.codex.englishcamera')
Invoke-Adb @('shell','mkdir','-p',$remoteModel)
Write-Host 'Copying the offline model (5.45 GB). Keep the USB cable connected...'
foreach($part in $manifest.model.files){Invoke-Adb @('push',(Join-Path $model $part.file),"$remoteModel/$($part.file)")}
Write-Host 'Checking copied files on the phone...'
foreach($part in $manifest.model.files){
    $line=@(Invoke-Adb @('shell','sha256sum',"$remoteModel/$($part.file)"))
    if(($line[0].Trim() -split '\s+')[0] -ne $part.sha256){throw "Phone file hash mismatch: $($part.file)"}
}
Invoke-Adb @('shell','am','start','-n','com.codex.englishcamera/.MainActivity')
Write-Host 'Installed. In See English, take/select a photo, then open Model recognition. No MNN Chat app or network is needed.'
