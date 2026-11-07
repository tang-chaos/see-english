@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-offline.ps1" -BundleRoot "%~dp0artifacts" -ModelPath "%~dp0model-work\vlm\Qwen3-VL-8B-Instruct-MNN" -ManifestPath "%~dp0artifacts\offline-bundle-0.8.5.json"
pause
