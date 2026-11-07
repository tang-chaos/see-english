param(
    [string]$Destination=(Join-Path $PSScriptRoot 'model'),
    [string]$ManifestPath=(Join-Path $PSScriptRoot 'model-manifest.json')
)
$ErrorActionPreference='Stop'
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$manifest=Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
if($manifest.repository -ne 'taobao-mnn/Qwen3-VL-8B-Instruct-MNN' -or $manifest.revision -ne '890e672be412e84d34bc318925aa4b7b61ad24c7'){
    throw 'This guide requires the pinned model version supported by See English 0.8.5.'
}
$folder=[IO.Path]::GetFullPath($Destination)
[IO.Directory]::CreateDirectory($folder) | Out-Null
function Get-Sha256([string]$Path) {
    $stream=[IO.File]::OpenRead($Path);$algorithm=[Security.Cryptography.SHA256]::Create()
    try {return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}
    finally {$algorithm.Dispose();$stream.Dispose()}
}
foreach($item in $manifest.files){
    $name=[string]$item.file
    if(!$name -or [IO.Path]::GetFileName($name) -ne $name -or $name -match '[/\\:]' -or $name -in @('.','..')){throw 'Unsafe model filename.'}
    if($item.sha256 -notmatch '^[a-f0-9]{64}$' -or [long]$item.bytes -le 0){throw 'Invalid checksum metadata.'}
    $target=Join-Path $folder $name
    if(Test-Path -LiteralPath $target){
        if((Get-Item -LiteralPath $target).Length -eq [long]$item.bytes -and (Get-Sha256 $target) -eq $item.sha256){Write-Host ('Verified existing: '+$name);continue}
        throw ('Existing file does not match this model version: '+$name+'. Use an empty destination or move the existing file yourself.')
    }
    $partial=$target+'.partial'
    $url='https://huggingface.co/'+$manifest.repository+'/resolve/'+$manifest.revision+'/'+[Uri]::EscapeDataString($name)+'?download=true'
    $verified=$false
    for($attempt=1;$attempt -le 3;$attempt++){
        $response=$null;$inputStream=$null;$outputStream=$null
        try {
            $start=if(Test-Path -LiteralPath $partial){(Get-Item -LiteralPath $partial).Length}else{0L}
            if($start -gt [long]$item.bytes){throw 'Partial file is larger than expected. Move it away before retrying.'}
            if($start -lt [long]$item.bytes){
                $request=[Net.HttpWebRequest]::Create($url)
                $request.UserAgent='SeeEnglish-OfficialModelGuide/0.8.5'
                $request.Timeout=90000;$request.ReadWriteTimeout=90000
                if($start -gt 0){$request.AddRange([long]$start)}
                $response=$request.GetResponse()
                $fileMode=[IO.FileMode]::Append
                if($start -gt 0 -and [int]$response.StatusCode -eq 200){
                    Write-Host 'Server returned a full response; restarting this partial download safely.'
                    $start=0L;$fileMode=[IO.FileMode]::Create
                } elseif($start -gt 0 -and ([int]$response.StatusCode -ne 206 -or !$response.Headers['Content-Range'].StartsWith(('bytes '+$start+'-')))){
                    throw 'Server returned an invalid resume range.'
                }
                $inputStream=$response.GetResponseStream()
                $outputStream=[IO.File]::Open($partial,$fileMode,[IO.FileAccess]::Write)
                $buffer=New-Object byte[] (1024*1024);$progress=[Diagnostics.Stopwatch]::StartNew()
                Write-Host ('Downloading '+$name+' from '+$start+' / '+$item.bytes+' bytes')
                while(($read=$inputStream.Read($buffer,0,$buffer.Length)) -gt 0){
                    $outputStream.Write($buffer,0,$read);$start+=$read
                    if($start -gt [long]$item.bytes){throw 'Download exceeds expected size.'}
                    if($progress.Elapsed.TotalSeconds -ge 10){Write-Host ($name+': '+[Math]::Round(100*$start/[double]$item.bytes,1)+'%');$progress.Restart()}
                }
                $outputStream.Dispose();$outputStream=$null
                $inputStream.Dispose();$inputStream=$null;$response.Close();$response=$null
            }
            if(!(Test-Path -LiteralPath $partial) -or (Get-Item -LiteralPath $partial).Length -ne [long]$item.bytes){throw 'Download is incomplete; rerun to resume.'}
            if((Get-Sha256 $partial) -ne $item.sha256){throw 'SHA-256 mismatch. Move the unverified partial file away before retrying.'}
            Move-Item -LiteralPath $partial -Destination $target
            Write-Host ('Verified: '+$name);$verified=$true;break
        } catch {
            if($attempt -eq 3){throw}
            Write-Host ('Retrying '+$name+': '+$_.Exception.Message)
            Start-Sleep -Seconds 3
        } finally {
            if($outputStream){$outputStream.Dispose()};if($inputStream){$inputStream.Dispose()};if($response){$response.Close()}
        }
    }
    if(!$verified){throw ('Could not verify '+$name)}
}
Write-Host 'Complete. All declared files passed size and SHA-256 checks.'
Write-Host 'Copy the model folder to the phone. In See English: Model recognition -> Settings -> Import offline model files.'
