#!/usr/bin/env bash
# Prueba que el proyecto sigue sano: lint + tipos + tests (backend y frontend) + build. Igual que el CI.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d backend/.venv ] && export PATH="$PWD/backend/.venv/bin:$PATH"
make check
