"""Download a pinned, public MNN vision model; resume and verify upstream hashes."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.request

REPO = "taobao-mnn/Qwen3-VL-8B-Instruct-MNN"
REVISION = "890e672be412e84d34bc318925aa4b7b61ad24c7"
ROOT = Path(__file__).resolve().parents[1] / "model-work" / "vlm"
TARGET = ROOT / "Qwen3-VL-8B-Instruct-MNN"


def download(item):
    name, size = item["rfilename"], item["size"]
    target = TARGET / name
    partial = target.with_name(target.name + ".part")
    expected = item.get("lfs", {}).get("sha256")
    if target.exists() and target.stat().st_size == size:
        data_hash = hashlib.file_digest(target.open("rb"), "sha256").hexdigest()
        if not expected or data_hash == expected:
            print(f"VERIFIED {name} {size} {data_hash}", flush=True)
            return {"file": name, "bytes": size, "sha256": data_hash, "upstream_sha256": expected}
        raise RuntimeError(f"Existing file failed hash: {name}")
    for attempt in range(8):
        start = partial.stat().st_size if partial.exists() else 0
        try:
            if start < size:
                # The official MNN ModelScope mirror serves identical large blobs;
                # accept them only against the pinned Hugging Face SHA-256.
                url = (f"https://modelscope.cn/models/MNN/Qwen3-VL-8B-Instruct-MNN/resolve/master/{name}"
                       if expected and os.environ.get('SEE_ENGLISH_DOWNLOAD_MIRROR','modelscope')=='modelscope' else f"https://huggingface.co/{REPO}/resolve/{REVISION}/{name}?download=true")
                request = urllib.request.Request(
                    url,
                    headers={"Range": f"bytes={start}-", "User-Agent": "EnglishCamera-LocalEvaluation/1.0"},
                )
                with urllib.request.urlopen(request, timeout=90) as response:
                    if start and response.status != 206:
                        raise RuntimeError("Server did not honor resume range")
                    if response.status == 206 and not response.headers.get("Content-Range", "").startswith(f"bytes {start}-"):
                        raise RuntimeError("Unexpected response range")
                    with partial.open("ab" if start else "wb") as output:
                        last = time.monotonic()
                        while chunk := response.read(1024 * 1024):
                            output.write(chunk)
                            start += len(chunk)
                            if time.monotonic() - last > 20:
                                print(f"PROGRESS {name} {start}/{size} ({start / size:.1%})", flush=True)
                                last = time.monotonic()
            if partial.stat().st_size != size:
                raise RuntimeError(f"Size mismatch: {partial.stat().st_size} != {size}")
            with partial.open("rb") as source:
                digest = hashlib.file_digest(source, "sha256").hexdigest()
            if expected and digest != expected:
                raise RuntimeError(f"Hash mismatch: {name}")
            partial.replace(target)
            print(f"VERIFIED {name} {size} {digest}", flush=True)
            return {"file": name, "bytes": size, "sha256": digest, "upstream_sha256": expected}
        except Exception as error:
            print(f"RETRY {name} attempt={attempt + 1} {type(error).__name__}: {error}", flush=True)
            if attempt == 7 or "Hash mismatch" in str(error):
                raise
            time.sleep(min(10, 1 + attempt * 2))


def main():
    TARGET.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(f"https://huggingface.co/api/models/{REPO}/revision/{REVISION}?blobs=true", timeout=60) as response:
        metadata = json.load(response)
    assert metadata["sha"] == REVISION
    (ROOT / "upstream-model.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    items = [item for item in metadata["siblings"] if item["rfilename"] != ".gitattributes"]
    print(f"START {REPO}@{REVISION}: {sum(item['size'] for item in items):,} bytes", flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        files = list(pool.map(download, items))
    manifest = {"repository": REPO, "revision": REVISION, "files": files, "total_bytes": sum(item['bytes'] for item in files)}
    (ROOT / "download-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print("COMPLETE All files verified; download-manifest.json written", flush=True)


if __name__ == "__main__":
    main()
