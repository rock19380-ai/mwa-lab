# Release

A signed RC1 revision 2 APK exists for local acceptance; it is not a final release.

## Phase 10 release-buildability checkpoint

Release identity is `dev.mwalab`, versionCode `1`, versionName
`0.1.0-clockin`. The `rc1` suffix belongs to the externally copied, verified
signed APK filename, not the in-app version. CI builds an **unsigned** release
APK to verify buildability; it is not the signed competition artifact.
Operator-signed RC1: signature and package verified; runtime acceptance pending.
Current candidate: `MWA-Lab-v0.1.0-clockin-rc1-r2.apk` from app source commit
`945295a3e0124af11a5d75a76c7444f09586339e`. The earlier RC1 APK and
October 4 device screenshots are historical, **superseded by the later Phase 10
RC1 revision**, not evidence for this binary.
Current signed-RC first run and cold Local MWA passed on the dedicated API-36
AVD. Live Devnet acceptance is waiting for user-supplied SOL to the current
installed RC identity; see
[`current-rc1-r2-funding-gate.md`](evidence/phase10/05-test-wallet/current-rc1-r2-funding-gate.md).

Remote MWA: `BLOCKED_HIDDEN` (not released); Remote QR scanner: `OMITTED`;
production-wallet compatibility: `NOT_VERIFIED`. No Phase 10 live Devnet
acceptance or final release freeze is asserted by this checkpoint.

For an operator-signed build, keep the stable keystore outside this repository
and provide `MWALAB_RELEASE_STORE_FILE` (absolute path),
`MWALAB_RELEASE_STORE_PASSWORD`, `MWALAB_RELEASE_KEY_ALIAS`, and
`MWALAB_RELEASE_KEY_PASSWORD` as environment variables. A partial configuration
fails rather than producing a misleading signed artifact. Never check in the
keystore or signing credentials; verify the resulting APK with `apksigner`
and `aapt` before distributing it. The signed artifact is not produced by CI.
Current verification and checksum are recorded in
[`02-build/signed-rc1-r2.md`](evidence/phase10/02-build/signed-rc1-r2.md);
[`02-build/signed-rc1.md`](evidence/phase10/02-build/signed-rc1.md) remains
historical evidence.

Release gates will eventually require:

- signed release APK;
- clean install;
- reproducible build;
- zero known P0/P1 defects;
- sanitized diagnostics;
- mainnet unavailable;
- deterministic canonical demo;
- matching Git commit/tag/APK/checksum.


## Phase 9 live acceptance (2026-10-04)

The installed protected disposable Test Wallet was manually funded on Devnet
and verified before live submission. The 1-lamport direct Test Wallet Send and
canonical NORMAL Local MWA memo sign-and-send both confirmed on Devnet. The
injected Local `FAULT_SIGN_REJECT` path returned `ERROR_NOT_SIGNED (-3)`
without signing approval or submission, and the final fault is `NORMAL`.
Current-run Room schema 4 evidence is in the [Phase 9 audit](evidence/phase9/09-adversarial-audit/live-and-security.md).
No airdrop or faucet was called in this continuation.

Remote MWA remains `BLOCKED_HIDDEN`; its scanner is `OMITTED`; Identity Reset
is `UNEXPOSED_OPTIONAL_P1`; production-wallet compatibility is
`NOT_VERIFIED`. Phase 9 live acceptance does not itself make a production
release candidate. A Phase 9 freeze requires a clean exact-head CI success and
annotated tag provenance.
