#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/phase9_static.sh
python3 scripts/phase10_static.py
