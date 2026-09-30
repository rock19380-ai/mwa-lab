#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
command -v adb >/dev/null
adb get-state >/dev/null

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
DEMO_APK="demo-client/build/outputs/apk/debug/demo-client-debug.apk"
test -f "$APP_APK"
test -f "$DEMO_APK"

adb install -r "$APP_APK"
adb install -r "$DEMO_APK"
adb shell am force-stop dev.mwalab || true
adb shell am force-stop dev.mwalab.democlient || true
adb shell monkey -p dev.mwalab -c android.intent.category.LAUNCHER 1 >/dev/null

echo
cat <<'EOF'
Phase 5 real cross-package acceptance launcher is ready.

Before each scenario, keep MWA Lab visible/ready. The Demo Client will open the
real MWA association path. In MWA Lab, tap SIMULATE and then follow the scenario:

  GOOD_PASS_APPROVE  -> expect simulation PASS, then tap APPROVE
  BAD_FAIL_APPROVE   -> expect simulation FAIL/SIMULATION, then tap APPROVE
  GOOD_PASS_REJECT   -> expect simulation PASS, then tap REJECT

The Demo Client is sign_transactions only. It must always show:
  Submitted transactions: 0

A simulation PASS is diagnostic evidence only; it does not guarantee later
submission or confirmation.
EOF

echo
for scenario in GOOD_PASS_APPROVE BAD_FAIL_APPROVE GOOD_PASS_REJECT; do
  echo "Launch with:"
  printf "adb shell am start -n dev.mwalab.democlient/.DemoClientActivity --es mwa_phase5_scenario %s\n" "$scenario"
done

echo
echo 'After confirming the lab identity has enough Devnet lamports, automated visible-UI acceptance can run all scenarios with:'
echo '  python3 scripts/phase5_device_acceptance.py --scenario ALL'
