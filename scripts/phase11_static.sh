#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/phase10_static.sh
python3 -m unittest discover -s scripts -p 'phase11_static_test.py'
python3 scripts/phase11_static.py
