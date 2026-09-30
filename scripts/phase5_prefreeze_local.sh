#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
EVIDENCE="$ROOT/docs/evidence/phase5"
mkdir -p "$EVIDENCE"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
LOG="$EVIDENCE/phase5-prefreeze-local-${STAMP}.txt"
exec > >(tee "$LOG") 2>&1

EXPECTED_BRANCH="phase5-simulation-diagnostic-classification"
PHASE4="6028375636251eb3071f0a5240a2ad1efc78131e"

printf '=== PHASE 5 PREFREEZE LOCAL GATE ===\n'
printf 'utc=%s\n' "$STAMP"
printf 'branch=%s\n' "$(git branch --show-current)"
printf 'head=%s\n' "$(git rev-parse HEAD)"
test "$(git branch --show-current)" = "$EXPECTED_BRANCH"
git merge-base --is-ancestor "$PHASE4" HEAD

echo '=== deterministic design/vector/static/security gates ==='
python3 scripts/phase5_design_check.py
python3 scripts/verify_phase5_simulation_vectors.py
python3 scripts/phase5_security_scan.py
./scripts/phase5_static.sh

echo '=== diff hygiene ==='
git diff --check

echo '=== Gradle lint/test/build ==='
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest

echo '=== APK existence ==='
test -f app/build/outputs/apk/debug/app-debug.apk
test -f demo-client/build/outputs/apk/debug/demo-client-debug.apk
find app/build/outputs/apk -type f -name '*.apk' -maxdepth 6 -print | sort
find demo-client/build/outputs/apk -type f -name '*.apk' -maxdepth 6 -print | sort

CONNECTED="NOT_RUN"
if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
  echo '=== connected Android instrumentation ==='
  adb devices -l
  ./gradlew :app:connectedDebugAndroidTest
  CONNECTED="PASS"
else
  echo 'connected Android instrumentation: NOT RUN (no adb device/emulator detected)'
fi

echo '=== protected predecessor hashes ==='
sha256sum \
  scripts/phase3_static.sh \
  scripts/phase4_static.sh \
  scripts/phase4_static.py \
  app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json \
  app/schemas/dev.mwalab.storage.MwaLabDatabase/2.json

echo '=== current state ==='
git status --short --branch

git diff --check

python3 - "$STAMP" "$CONNECTED" "$LOG" <<'PY'
import json, subprocess, sys
from pathlib import Path
stamp, connected, log = sys.argv[1:]
root = Path.cwd()
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
out = {
  'phase': '5.12-5.15-prefreeze',
  'status': 'PASS',
  'utc': stamp,
  'branch': git('branch','--show-current'),
  'headBeforeCloseoutCommit': git('rev-parse','HEAD'),
  'roomSchema': 3,
  'designCheck': 'PASS',
  'deterministicVectors': 'PASS',
  'securityStaticScan': 'PASS',
  'phase5Static': 'PASS',
  'gitDiffCheck': 'PASS',
  'gradleLint': 'PASS',
  'gradleTest': 'PASS',
  'assembleDebug': 'PASS',
  'assembleAndroidTest': 'PASS',
  'connectedDebugAndroidTest': connected,
  'liveDevnetAcceptance': 'PENDING_SEPARATE_DEVICE_FLOW',
  'freezeTagCreated': False,
  'log': str(Path(log).relative_to(root)),
}
path=root/'docs/evidence/phase5/phase5-prefreeze-local-gates.json'
path.write_text(json.dumps(out, indent=2)+'\n')
print(path.read_text(), end='')
PY

echo 'PHASE 5 PREFREEZE LOCAL GATE: PASS'
echo "log=$LOG"
