"""Package the current APK plus pinned model, verify them, and split for GitHub Releases."""
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
VERSION='0.8.5'
APK=ROOT/f'artifacts/see-english-{VERSION}-arm64-debug.apk'
PART_BYTES=512*1024*1024  # Every release asset must be below 2 GiB.

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-dir',type=Path,default=ROOT/'model-work/vlm/Qwen3-VL-8B-Instruct-MNN')
    args=parser.parse_args()
    model=args.model_dir.resolve()
    manifest=json.loads((ROOT/'app/src/main/assets/vlm-model.json').read_text(encoding='utf-8'))
    assert sha(APK)==APK.with_suffix('.apk.sha256').read_text().strip(),'APK checksum mismatch'
    with zipfile.ZipFile(APK) as apk:
        assert json.loads(apk.read('assets/vlm-model.json'))==manifest
        for name in ['libMNN.so','libmnnllmapp.so']:
            assert hashlib.sha256(apk.read('lib/arm64-v8a/'+name)).hexdigest()==sha(ROOT/'app/src/main/jniLibs/arm64-v8a'/name)
    for part in manifest['files']:
        assert Path(part['file']).name==part['file']
        source=model/part['file']
        assert source.stat().st_size==part['bytes'] and sha(source)==part['sha256'],part['file']
    info={'version':VERSION,'apk':{'file':APK.name,'bytes':APK.stat().st_size,'sha256':sha(APK)},
          'model':manifest,'phone_tested':False,'internet_required':False}
    archive=ROOT/f'artifacts/see-english-{VERSION}-offline-arm64.zip'
    prefix=f'see-english-{VERSION}-offline/'
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_STORED,allowZip64=True) as bundle:
        bundle.writestr(prefix+'bundle-manifest.json',json.dumps(info,ensure_ascii=False,indent=2))
        bundle.write(ROOT/'docs/offline-install.txt',prefix+'INSTALL.txt')
        bundle.writestr(prefix+'install.cmd','@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-offline.ps1"\r\npause\r\n')
        for source,name in [(ROOT/'scripts/install-offline.ps1','install-offline.ps1'),(APK,APK.name),
                            (ROOT/'LICENSE','LICENSE'),(ROOT/'NOTICE','NOTICE'),(ROOT/'THIRD_PARTY.md','THIRD_PARTY.md'),
                            (ROOT/'PRIVACY.md','PRIVACY.md'),(ROOT/'licenses/MNN-LICENSE.txt','MNN-LICENSE.txt'),
                            (ROOT/'licenses/QWEN-LICENSE.txt','QWEN-LICENSE.txt')]:bundle.write(source,prefix+name)
        for part in manifest['files']:
            print('Packing',part['file'],part['bytes'],flush=True)
            bundle.write(model/part['file'],prefix+'model/'+part['file'])
    print('Checking ZIP CRCs...',flush=True)
    with zipfile.ZipFile(archive) as bundle:assert bundle.testzip() is None
    checksum=sha(archive)
    archive.with_suffix('.zip.sha256').write_text(checksum+'\n',encoding='ascii')
    info['archive']={'file':archive.name,'bytes':archive.stat().st_size,'sha256':checksum,'crc_verified':True}
    (ROOT/f'artifacts/offline-bundle-{VERSION}.json').write_text(json.dumps(info,ensure_ascii=False,indent=2),encoding='utf-8')
    parts=[]
    with archive.open('rb') as stream:
        index=1
        while stream.tell()<archive.stat().st_size:
            path=archive.with_name(archive.name+f'.part{index:02d}')
            digest=hashlib.sha256();written=0
            with path.open('wb') as output:
                while written<PART_BYTES:
                    chunk=stream.read(min(8*1024*1024,PART_BYTES-written))
                    if not chunk:break
                    output.write(chunk);digest.update(chunk);written+=len(chunk)
            parts.append({'file':path.name,'bytes':written,'sha256':digest.hexdigest()})
            print('Split',path.name,written,flush=True);index+=1
    release={'version':VERSION,'archive':info['archive'],'parts':parts}
    (ROOT/f'artifacts/offline-parts-{VERSION}.json').write_text(json.dumps(release,indent=2),encoding='utf-8')
    shutil.copy2(ROOT/'scripts/merge-offline.ps1',ROOT/'artifacts/merge-offline.ps1')
    checks=[(APK,sha(APK))]+[(ROOT/'artifacts'/item['file'],item['sha256']) for item in parts]
    for name in [f'offline-parts-{VERSION}.json',f'offline-bundle-{VERSION}.json','merge-offline.ps1']:
        path=ROOT/'artifacts'/name;checks.append((path,sha(path)))
    (ROOT/'artifacts/SHA256SUMS.txt').write_text(''.join(f'{digest}  {path.name}\n' for path,digest in checks),encoding='ascii')
    print(json.dumps(release,indent=2),flush=True)

if __name__=='__main__':main()
