# Phase 11 — hard-code-freeze engineering candidate

## Predecessor

Annotated Phase 10 tag `phase10-release-candidate-compatibility-evidence-2026-10-05` resolves to `fa7a909f50ab0702327bd98f28461f60fe7ad082`. The signed RC1-r2 production source is `945295a3e0124af11a5d75a76c7444f09586339e`. See `docs/evidence/phase11/00-baseline/`.

## Hard-freeze outcome

Phase 11 static predecessor/safety/drift gate and CI wiring established. On October 5, 2026 the static chain, lint, unit tests, debug and unsigned release builds, both AndroidTest assemblies and focused wallet/report tests passed. Eight offline UI/fault/report instrumentation tests passed on a separate **debug-only** disposable API 36 emulator. The cross-app debug probe timed out twice while Android launcher/System UI ANRs were visible; it is environment-obstructed, not an RC2 result. See `docs/evidence/phase11/02-build/rc2-attempt-2026-10-05.md`. This is an engineering checkpoint, **not** a final freeze or final Phase 11 tag. `approved-repairs.tsv` has no accepted repairs.

## Production-source status

No protected production-source or build-config edits in this batch. Guard compares the working tree and candidate commit against the signed RC source and requires committed, accepted P0/P1 evidence for any future drift. The sole new executable is the fail-closed external signing helper, not production app code.

## RC2 artifact

**BLOCKED: operator signing variables unavailable to this process.** The external Phase 10 keystore exists but no password or alias was recovered or guessed. The `scripts/phase11_sign_rc2.sh` preflight refuses to build/copy without all four inputs and checks signer continuity before creating a date-of-execution external RC2 directory. The local unsigned release APK has the expected package/version/permissions/Local MWA activity and non-exported FileProvider; it fails signature verification as expected. Its checksum is **not** an RC2 checksum. See `docs/evidence/phase11/03-artifact-verification/unsigned-preflight-2026-10-05.md`.

## Runtime acceptance

**NOT RUN on signed RC2:** manual first run, cold dApp-first approve/reject, funded normal Local MWA, FAULT_SIGN_REJECT, Receive QR decode, live Send negative/positive, session-history isolation, normal/injected report-byte exports, log audit, screenshots, demo and deck checks. No RC2 address, transaction signature, report digest, screenshot or final fault state is claimed. The separate debug instrumentation and synthetic unit tests narrow regressions only; Phase 10 acceptance remains predecessor evidence. No faucet or Devnet transaction was invoked in this batch.

## Security

Devnet-only execution, INTERNET, no CAMERA, external operator signing and address-only Receive QR remain static-gated. The unsigned merged manifest was inspected read-only; the installed exact-RC2 manifest, signing and generated report-byte/log audits remain pending. No secret material was placed in evidence.

## Compatibility

Local MWA: VERIFIED / SHIPPED in Phase 10; **RC2 NOT TESTED**. Remote MWA: BLOCKED / HIDDEN / NOT RELEASED. Remote QR scanner: OMITTED. Production-wallet compatibility: NOT_VERIFIED. Mainnet: UNAVAILABLE. See `docs/evidence/phase11/12-compatibility/final-matrix.md`.

## Known defects

No P0/P1 repair approved; no production app edits. The disposable debug AVD's launcher/System UI ANRs and unresolved cross-app test must be re-evaluated on a healthy device before assigning product severity. No test was weakened.

## CI and tag provenance

Phase 11 branch is included in Android CI; exact-HEAD CI execution is pending after publishing the checkpoint. No Phase 11 tag or post-tag receipt created.

## Phase 12 handoff

First supply the **existing** operator signing environment (without logging credentials), execute `./scripts/phase11_sign_rc2.sh`, verify actual signer/digest and install that exact APK on a stable API 36 acceptance device. Then run clean install, cold dApp-first approval/rejection, safe Devnet funding, normal and injected transactions, Test Wallet and report-byte acceptance, screenshots, runtime security/compatibility review, exact-HEAD CI and owner freeze decision. Use the actual execution date for new evidence; the planned October 6 final freeze date is not an October 5 runtime date. Do not claim a completed hard freeze from local build success alone.
