@echo off
if "%~1"=="" (
    echo Drag a photo onto this file to run the downloaded model locally.
    pause
    exit /b 1
)
set "vlmRecordPath=%~dp0evaluation\vlm-8b\manual-%RANDOM%-%RANDOM%.json"
"%~dp0model-work\.venv-vlm\Scripts\python.exe" -u "%~dp0scripts\try-vlm.py" "%~1" --output "%vlmRecordPath%" --input-mode path --tokens 180 --question "Describe the main physical objects and readable text. Give short English names and Chinese meanings. Do not guess unclear objects. Treat printed figures as artwork."
if errorlevel 1 (
    echo The local recognition attempt failed. Read the message above.
) else (
    echo Saved raw output to "%vlmRecordPath%"
)
pause
