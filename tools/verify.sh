#!/usr/bin/env bash
# Prueba que el proyecto sigue sano: lint + tipos + tests (backend y frontend) + build (= CI),
# y la app Android (tests KMP + app + APK). SKIP_ANDROID=1 para saltarla.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d backend/.venv ] && export PATH="$PWD/backend/.venv/bin:$PATH"
make check
[ "${SKIP_ANDROID:-0}" = "1" ] || make android-check
