"""Audit the lean APK, excluded engines and current local tests. SDK signing checks are separate."""
import hashlib,json,zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
VERSION='0.8.5'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    path=ROOT/f'artifacts/see-english-{VERSION}-arm64-debug.apk'
    checksum=sha(path)
    assert checksum==path.with_suffix('.apk.sha256').read_text().strip()
    with zipfile.ZipFile(path) as apk:
        names=apk.namelist()
        weights=[n for n in names if n.startswith('assets/') and n.endswith(('.onnx','.tflite'))]
        assert weights==['assets/efficientdet_lite0.tflite'],weights
        for asset in ['efficientdet_lite0.tflite','words.txt','words-extra.txt','words-wide.txt','vlm-model.json','MNN-LICENSE.txt']:
            assert apk.read('assets/'+asset)==(ASSETS/asset).read_bytes(),asset
        for name in names:
            assert not any(s in name.lower() for s in ['onnxruntime','mlkit','ocr-check','model-canaries','sample.jpg','photo-replay','arbitration-replay','vlm-package-name']),name
        dex=b''.join(apk.read(n) for n in names if n.endswith('.dex'))
        for removed in ['展开简单例句','收起例句','听例句','拍照识物','相机学习','使用指南','读字识词','查看文字','广识更多','重新广识','分区找遗漏','模式：','8B离线','8B精识别','8B词卡','开始 8B 离线识别']:
            assert removed.encode('utf-8') not in dex,removed
        assert b'ai/onnxruntime' not in dex and b'com/google/mlkit' not in dex
        for native in ['libMNN.so','libmnnllmapp.so']:
            assert hashlib.sha256(apk.read('lib/arm64-v8a/'+native)).hexdigest()==sha(ROOT/'app/src/main/jniLibs/arm64-v8a'/native)
        abis=sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})
        assert abis==['arm64-v8a']
    assert path.stat().st_size<60*1024*1024,'Lean APK unexpectedly exceeds 60 MiB'
    reports=list((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    assert reports
    tests={key:sum(int(ET.parse(p).getroot().get(key,'0')) for p in reports) for key in ['tests','failures','errors','skipped']}
    assert tests['failures']==tests['errors']==tests['skipped']==0
    lint=(ROOT/'app/build/reports/lint-results-debug.txt').read_text(encoding='utf-8')
    assert '0 errors,' in lint
    package={'file':path.name,'bytes':path.stat().st_size,'sha256':checksum,'abis':abis,'packaged_assets_match':True}
    result={'version':VERSION,'packages':[package],'local_tests':tests,'lint_summary':lint.strip().splitlines()[-1],
        'physical_device_validated':False,'removed_legacy_onnx_and_ocr':True,'realtime_classes':80,
        'note':'Current camera/vocabulary/cancellation/UI paths were checked locally; retired model tests are archived. Android device behavior remains unverified for this release.'}
    (ROOT/f'artifacts/verification-{VERSION}.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    metadata=ROOT/f'artifacts/offline-bundle-{VERSION}.json'
    bundle=json.loads(metadata.read_text(encoding='utf-8')) if metadata.exists() else {}
    bundle.update({'version':VERSION,'apk':package,'model':json.loads((ASSETS/'vlm-model.json').read_text(encoding='utf-8'))})
    metadata.write_text(json.dumps(bundle,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
