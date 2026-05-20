#!/usr/bin/env bash
# RelayBridge one-click launcher (Linux / macOS)
# Creates a local virtualenv, installs deps, runs the setup wizard
# if needed, then starts the proxy.

set -e
cd "$(dirname "$0")"

VENV_DIR=".venv"

find_python() {
    for cmd in python3.12 python3.11 python3.10 python3 python; do
        if command -v "$cmd" >/dev/null 2>&1; then
            ver=$("$cmd" -c 'import sys;print("%d.%d"%sys.version_info[:2])' 2>/dev/null || echo "0.0")
            major=${ver%.*}; minor=${ver#*.}
            if [ "$major" -ge 3 ] && [ "$minor" -ge 10 ]; then
                echo "$cmd"
                return 0
            fi
        fi
    done
    return 1
}

PY=$(find_python) || {
    echo "[X] Python 3.10+ not found. Install it and re-run this script." >&2
    exit 1
}

if [ ! -x "$VENV_DIR/bin/python" ]; then
    echo "[*] Creating virtual environment in $VENV_DIR ..."
    "$PY" -m venv "$VENV_DIR"
fi

VPY="$VENV_DIR/bin/python"

# Skip dependency install when all required packages are already importable.
# Pip install takes time every launch; this drops warm runs to <0.1s.
# Falls through to the install path on first run or if any import fails for
# any reason.
if "$VPY" -c "import cryptography, h2, brotli, zstandard" >/dev/null 2>&1; then
    echo "[*] Dependencies already installed — skipping pip install."
else
    echo "[*] Installing dependencies from runflare mirror. Pip download progress will be shown below ..."
    PIP_INDEX_ARGS=(-i https://mirror-pypi.runflare.com/simple/ --trusted-host mirror-pypi.runflare.com)
    "$VPY" -m pip install --disable-pip-version-check "${PIP_INDEX_ARGS[@]}" --upgrade pip
    "$VPY" -m pip install --disable-pip-version-check "${PIP_INDEX_ARGS[@]}" -r requirements.txt
fi

if [ ! -f "config.json" ]; then
    echo "[*] No config.json found — launching setup wizard ..."
    "$VPY" setup.py
fi

echo
echo "[*] Starting RelayBridge ..."
echo
exec "$VPY" main.py "$@"
