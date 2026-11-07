"""Run the downloaded MNN model locally on a photo. No network requests."""
import argparse
import json
from pathlib import Path
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--threads", type=int, default=6)
    parser.add_argument("--tokens", type=int, default=256)
    parser.add_argument("--size", type=int, default=672)
    parser.add_argument("--question", help="Optional neutral comparison prompt; recorded verbatim")
    parser.add_argument("--input-mode", choices=["tensor", "path"], default="tensor")
    args = parser.parse_args()
    import MNN.llm as llm
    import MNN.cv as cv
    from PIL import Image

    root = Path(__file__).resolve().parents[1]
    model_dir = root / "model-work/vlm/Qwen3-VL-8B-Instruct-MNN"
    if not (model_dir.parent / "download-manifest.json").exists():
        raise RuntimeError("Complete and verify the model download before inference")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image = Image.open(args.image).convert("RGB")
    original_size = image.size
    image.thumbnail((args.size, args.size))
    processed = args.output.with_name(args.output.stem + "-input.png")
    image.save(processed)
    # The upstream model defaults to 16K output tokens. Limit this experiment;
    # do not modify the downloaded Android model configuration.
    print("Loading Qwen3-VL-8B-Instruct INT4 on CPU...", flush=True)
    model = llm.create(str(model_dir / "config.json"))
    model.set_config({"backend_type": "cpu", "thread_num": args.threads,
                      "max_new_tokens": args.tokens, "sampler_type": "greedy",
                      "reuse_kv": False,
                      "mllm": {"backend_type": "cpu", "thread_num": args.threads,
                               "precision": "low", "memory": "low"}})
    started = time.monotonic()
    model.load()
    load_seconds = time.monotonic() - started
    print(f"Loaded in {load_seconds:.1f}s; processing image {image.size}...", flush=True)
    # No candidate labels or expected answers are supplied in the prompt.
    question = ("List the clearly visible physical objects in this image, using short English nouns. "
                "Treat printed pictures on packaging or fabric as artwork, not real objects. "
                "Ignore app interface text and detection labels if present. "
                "Identify body parts by the visible part rather than assuming a whole person. "
                "Use readable package text to help identify its contents. "
                "Do not guess unclear objects. Return only a JSON object: "
                '{"objects":[{"name":"English noun","evidence":"short visible evidence"}]}. '
                "List at most 12 objects. No introduction.")
    question = args.question or question
    prompt = (f"<img>{processed.resolve().as_posix()}</img>" + question if args.input_mode == "path" else
              {"text": "<img>image_0</img>" + question,
               "images": [{"data": cv.imread(str(processed.resolve())),
                           "height": image.height, "width": image.width}]})
    started = time.monotonic()
    answer = model.response(prompt, False)
    inference_seconds = time.monotonic() - started
    ctx = model.context
    metrics = {name: getattr(ctx, name, None) for name in
               ["vision_us", "prefill_us", "decode_us", "prompt_len", "gen_seq_len", "pixels_mp"]}
    record = {"model": "taobao-mnn/Qwen3-VL-8B-Instruct-MNN", "runtime": "MNN 3.6.2 Windows CPU",
              "source_image": str(args.image.resolve()), "original_size": original_size,
              "input_size": image.size, "prompt": question, "output": answer,
              "load_seconds": load_seconds, "inference_seconds": inference_seconds,
              "threads": args.threads, "max_new_tokens": args.tokens, "metrics": metrics,
              "input_mode": args.input_mode,
              "status": str(getattr(ctx, "status", "unknown")),
              "device_validation": False}
    args.output.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(record, ensure_ascii=True, indent=2), flush=True)


if __name__ == "__main__":
    main()
