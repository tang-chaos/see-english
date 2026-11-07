"""Audit the retained realtime model and lightweight learning dictionary."""
import hashlib,json,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
model=ASSETS/'efficientdet_lite0.tflite'
checksum=hashlib.sha256(model.read_bytes()).hexdigest()
assert checksum=='0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb'
assert sorted(p.name for p in ASSETS.glob('*.tflite'))==['efficientdet_lite0.tflite']
assert not list(ASSETS.glob('*.onnx'))
with zipfile.ZipFile(model) as z:labels=set(z.read('labels.txt').decode().splitlines())-{'???',''}
rows=[line.split('|') for p in ASSETS.glob('words*.txt') for line in p.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#')]
assert len(rows)==519 and len({r[0] for r in rows})==519
assert all(len(r)==4 and all(r) and r[2].startswith('/') and r[2].endswith('/') for r in rows)
assert len(labels)==80 and labels<={r[0] for r in rows}
print(json.dumps({'status':'PASS','realtime_model_bytes':model.stat().st_size,'supported_realtime_classes':80,
                  'learning_cards':len(rows),'realtime_model_sha256':checksum,'legacy_detection_weights':False}))
