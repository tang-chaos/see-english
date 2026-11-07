$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$testRoot=Join-Path $projectRoot ('build/offline-installer-smoke/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $testRoot 'model') -Force > $null
Set-Content -LiteralPath (Join-Path $testRoot 'fixture.apk') -Value 'installer fixture only' -Encoding ascii
Set-Content -LiteralPath (Join-Path $testRoot 'model/weight.bin') -Value 'tiny model fixture only' -Encoding ascii
$apk=Get-Item (Join-Path $testRoot 'fixture.apk');$weight=Get-Item (Join-Path $testRoot 'model/weight.bin')
$manifest=@{apk=@{file=$apk.Name;sha256=(Get-FileHash $apk.FullName).Hash.ToLowerInvariant()};model=@{files=@(@{file=$weight.Name;bytes=$weight.Length;sha256=(Get-FileHash $weight.FullName).Hash.ToLowerInvariant()})}}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $testRoot 'bundle-manifest.json') -Encoding ascii
$mock=@'
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$AdbArguments)
$global:LASTEXITCODE=0
$root=$env:SEE_ENGLISH_INSTALL_TEST_ROOT
ConvertTo-Json -InputObject $AdbArguments -Compress | Add-Content -LiteralPath (Join-Path $root 'calls.jsonl')
if($AdbArguments[0] -eq 'devices'){'List of devices attached';'fixture-device device';return}
if($AdbArguments[0] -ne '-s' -or $AdbArguments[1] -ne 'fixture-device'){throw 'Unexpected device selection'}
$command=@($AdbArguments | Select-Object -Skip 2)
switch($command[0]){
    'install' {if($command[1] -ne '-r' -or !(Test-Path $command[2])){throw 'Bad install arguments'};'Success';return}
    'push' {if(!(Test-Path $command[1]) -or $command[2] -ne '/sdcard/Android/data/com.codex.englishcamera/files/models/Qwen3-VL-8B-Instruct-MNN/weight.bin'){throw 'Bad model copy destination'};'1 file pushed';return}
    'shell' {
        switch($command[1]){
            'df' {'Filesystem 1K-blocks Used Available Use% Mounted on';'/dev/test 40000000 1000000 39000000 3% /data';return}
            'am' {return}
            'mkdir' {return}
            'sha256sum' {$m=Get-Content -Raw (Join-Path $root 'bundle-manifest.json') | ConvertFrom-Json;"$($m.model.files[0].sha256)  $($command[2])";return}
        }
    }
}
throw ('Unexpected adb command: '+($AdbArguments -join ' '))
'@
$mockPath=Join-Path $testRoot 'mock-adb.ps1'
Set-Content -LiteralPath $mockPath -Value $mock -Encoding ascii
$env:SEE_ENGLISH_INSTALL_TEST_ROOT=$testRoot
& (Join-Path $PSScriptRoot 'install-offline.ps1') -BundleRoot $testRoot -AdbPath $mockPath
$calls=@(Get-Content (Join-Path $testRoot 'calls.jsonl') | ForEach-Object {,($_ | ConvertFrom-Json)})
if(!($calls | Where-Object {$_ -contains 'push'})){throw 'Model transfer was never invoked'}
$before=$calls.Count
Set-Content -LiteralPath $weight.FullName -Value 'corrupt data' -Encoding ascii
$rejected=$false
try {& (Join-Path $PSScriptRoot 'install-offline.ps1') -BundleRoot $testRoot -AdbPath $mockPath}catch{$rejected=$true}
if(!$rejected){throw 'Corrupt model was accepted'}
$later=@(Get-Content (Join-Path $testRoot 'calls.jsonl') | Select-Object -Skip $before)
if($later | Where-Object {$_ -match '"install"|"push"'}){throw 'Device mutation occurred after local corruption'}
Write-Host 'PASS: installer command flow and corrupt-file rejection (mock adb; no physical phone actions).'
