# Release

No release candidate exists yet.

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
