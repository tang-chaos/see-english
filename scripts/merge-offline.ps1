param([string]$ManifestPath=(Join-Path $PSScriptRoot 'offline-parts-0.8.5.json'))
$ErrorActionPreference='Stop'
$manifestFile=Get-Item -LiteralPath $ManifestPath
$folder=$manifestFile.Directory.FullName
$manifest=Get-Content -Raw -LiteralPath $manifestFile.FullName | ConvertFrom-Json
function Safe-Name([string]$Name) {
    if(!$Name -or [IO.Path]::GetFileName($Name) -ne $Name -or $Name -match '[/\\:]' -or $Name -in @('.','..')){throw 'Unsafe file name in manifest.'}
    return Join-Path $folder $Name
}
function Check-File([string]$Path,$Expected) {
    if($Expected.sha256 -notmatch '^[a-f0-9]{64}$' -or [long]$Expected.bytes -le 0){throw 'Invalid checksum metadata.'}
    if(!(Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -ne [long]$Expected.bytes){throw "Missing/incomplete file: $([IO.Path]::GetFileName($Path))"}
    if((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Expected.sha256){throw "Checksum mismatch: $([IO.Path]::GetFileName($Path))"}
}
$target=Safe-Name $manifest.archive.file
if(Test-Path -LiteralPath $target){Check-File $target $manifest.archive;Write-Host 'The complete archive already exists and passed verification.';return}
$index=1;$total=0L
foreach($part in $manifest.parts){
    if($part.file -ne ($manifest.archive.file+('.part{0:D2}' -f $index))){throw 'Unexpected part name/order.'}
    Check-File (Safe-Name $part.file) $part
    $total += [long]$part.bytes;$index++
}
if($index -eq 1 -or $total -ne [long]$manifest.archive.bytes){throw 'Incomplete archive manifest.'}
$temporary=$target+'.merging'
$output=[IO.File]::Open($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
try {
    foreach($part in $manifest.parts){
        Write-Host ('Merging '+$part.file)
        $inputStream=[IO.File]::OpenRead((Safe-Name $part.file))
        try {$inputStream.CopyTo($output)} finally {$inputStream.Dispose()}
    }
} finally {$output.Dispose()}
Check-File $temporary $manifest.archive
Move-Item -LiteralPath $temporary -Destination $target
Write-Host ('Verified: '+$manifest.archive.file)
Write-Host 'Extract the complete ZIP with a ZIP64-capable tool, then read INSTALL.txt.'
