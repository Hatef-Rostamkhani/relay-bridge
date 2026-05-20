@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM -------- RelayBridge one-click launcher (Windows) --------
REM Creates a local virtualenv, installs deps, runs the setup wizard
REM if needed, then starts the proxy. Also checks and installs CA cert
REM if not already trusted.

set "VENV_DIR=.venv"
set "PY="

where py >nul 2>&1
if !errorlevel!==0 (
    set "PY=py -3"
) else (
    where python >nul 2>&1
    if !errorlevel!==0 (
        set "PY=python"
    )
)

if "%PY%"=="" (
    echo [X] Python 3.10+ was not found on PATH.
    echo     Install from https://www.python.org/downloads/ and re-run this script.
    pause
    exit /b 1
)

if not exist "%VENV_DIR%\Scripts\python.exe" (
    echo [*] Creating virtual environment in %VENV_DIR% ...
    %PY% -m venv "%VENV_DIR%"
    if errorlevel 1 (
        echo [X] Failed to create virtualenv.
        pause
        exit /b 1
    )
)

set "VPY=%VENV_DIR%\Scripts\python.exe"

REM -------- Skip dependency install when all required packages are already importable.
REM Pip install takes time every launch; this drops warm runs to ~0.1s.
REM Falls through to the install path on first run, after a requirements.txt
REM change, or when any import fails for any reason.
set "DEPS_OK=0"
"%VPY%" -c "import cryptography, h2, brotli, zstandard" >nul 2>&1
if !errorlevel!==0 set "DEPS_OK=1"

if "!DEPS_OK!"=="1" (
    echo [*] Dependencies already installed — skipping pip install.
) else (
    echo [*] Installing dependencies from runflare mirror. Pip download progress will be shown below ...
    "%VPY%" -m pip install --disable-pip-version-check --upgrade pip ^
        -i https://mirror-pypi.runflare.com/simple/ ^
        --trusted-host mirror-pypi.runflare.com
    if errorlevel 1 (
        echo [X] Could not upgrade pip.
        pause
        exit /b 1
    )
    "%VPY%" -m pip install --disable-pip-version-check -r requirements.txt ^
        -i https://mirror-pypi.runflare.com/simple/ ^
        --trusted-host mirror-pypi.runflare.com
    if errorlevel 1 (
        echo [X] Could not install dependencies.
        pause
        exit /b 1
    )
)

if not exist "config.json" (
    echo [*] No config.json found — launching setup wizard ...
    "%VPY%" setup.py
    if errorlevel 1 (
        echo [X] Setup cancelled.
        pause
        exit /b 1
    )
)

REM -------- Check for uninstall flag --------
echo %* | findstr /C:"--uninstall-cert" >nul
if not errorlevel 1 (
    echo [*] Uninstalling CA certificate ...
    "%VPY%" main.py --uninstall-cert
    exit /b %errorlevel%
)


echo.
echo [*] Starting RelayBridge ...
echo.
"%VPY%" main.py %*
set "RC=%errorlevel%"
if not "%RC%"=="0" pause
exit /b %RC%
