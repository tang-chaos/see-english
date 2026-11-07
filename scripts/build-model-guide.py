"""Create a small official-source model download guide; no model weights or Actions needed."""
import hashlib
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
VERSION='0.8.5'
target=ROOT/f'artifacts/see-english-{VERSION}-model-download-guide.zip'
prefix=f'see-english-{VERSION}-model-download-guide/'
with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED) as bundle:
    bundle.write(ROOT/'docs/model-download.md',prefix+'README.txt')
    bundle.write(ROOT/'scripts/download-official-model.ps1',prefix+'download-model.ps1')
    bundle.write(ROOT/'app/src/main/assets/vlm-model.json',prefix+'model-manifest.json')
    bundle.write(ROOT/'LICENSE',prefix+'LICENSE')
    bundle.write(ROOT/'licenses/QWEN-LICENSE.txt',prefix+'QWEN-LICENSE.txt')
    bundle.writestr(prefix+'download-model.cmd','@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0download-model.ps1"\r\npause\r\n')
with zipfile.ZipFile(target) as bundle:assert bundle.testzip() is None
digest=hashlib.sha256(target.read_bytes()).hexdigest()
target.with_suffix('.zip.sha256').write_text(digest+'\n',encoding='ascii')
print(f'{target.name}: {target.stat().st_size} bytes, SHA256 {digest}')
