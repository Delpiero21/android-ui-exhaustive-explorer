@echo off
REM android-ui-exhaustive-explorer - first-time setup
REM Idempotent: safe to run multiple times.

cd /d "%~dp0"

echo.
echo ================================================================
echo  android-ui-exhaustive-explorer - first-time setup
echo ================================================================
echo.

REM ----- check conda -----
where /q conda
if errorlevel 1 (
    echo [ERROR] conda not found in PATH.
    echo         Run from Anaconda Prompt, or:
    echo            conda init cmd
    echo            conda init powershell
    echo         Then open a new shell and retry.
    pause
    exit /b 1
)

REM ----- conda env "explorer" -----
echo [1/3] conda env "explorer"
call conda env list | findstr /b /c:"explorer " >nul
if errorlevel 1 (
    echo       creating with python 3.12 ...
    call conda create -n explorer python=3.12 -y
    if errorlevel 1 (
        echo [ERROR] conda env creation failed
        pause
        exit /b 1
    )
) else (
    echo       already exists, skipping.
)

REM ----- server pip install -----
echo.
echo [2/3] server pip install (in env "explorer")
call conda run -n explorer pip install -e "%~dp0server[dev]"
if errorlevel 1 (
    echo [ERROR] pip install failed
    pause
    exit /b 1
)

REM ----- web npm install -----
echo.
echo [3/3] web npm install
where /q npm
if errorlevel 1 (
    echo [ERROR] npm not found. Install Node.js v20+:
    echo            https://nodejs.org/
    pause
    exit /b 1
)
pushd "%~dp0web"
call npm install
if errorlevel 1 (
    popd
    echo [ERROR] npm install failed
    pause
    exit /b 1
)
popd

echo.
echo ================================================================
echo  Setup complete. Now double-click dev.bat to run.
echo ================================================================
echo.
pause
