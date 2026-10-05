# Phase 11 — hard-code-freeze engineering and signed-RC2 runtime candidate

## Predecessor

Annotated Phase 10 tag `phase10-release-candidate-compatibility-evidence-2026-10-05` resolves to `fa7a909f50ab0702327bd98f28461f60fe7ad082`; accepted signed RC production source `945295a3e0124af11a5d75a76c7444f09586339e`. Baseline evidence: `docs/evidence/phase11/00-baseline/`.

## Hard-freeze outcome

On October 5, 2026, Phase 9/10/11 static gates, lint, unit tests, debug and unsigned release builds, both AndroidTest assemblies and focused offline wallet/report tests passed in the engineering baseline batch. The static chain passed again after the signed-RC2 evidence update. Separate debug instrumentation is not represented as signed-RC2 runtime proof. No P0/P1 repair approved; Phase 11 is **not finally frozen or tagged**.

## Production-source status

Protected `app/src/main/**`, `demo-client/src/main/**`, `app/build.gradle.kts` and `gradle/libs.versions.toml` remain identical to the Phase 10 signed production-source baseline. This runtime batch changed documentation/evidence/screenshots only; no dependency upgrade, app change or RC2 rebuild. The evidence checkpoint HEAD must not be confused with the immutable artifact source commit.

## RC2 artifact

Existing operator-signed release was **not rebuilt/re-signed**: `/home/abbaas/Downloads/MWA_LAB_RC2_2026-10-05/MWA-Lab-v0.1.0-clockin-rc2.apk`; source `4b97796ab7958b2590d32271de8e0a0786cbc824`; APK SHA-256 `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`; signer SHA-256 `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4`, matching Phase 10. Package `dev.mwalab`, versionCode 1, versionName `0.1.0-clockin`, minSdk 23, targetSdk 37. Installed base.apk digest matched exact RC2 on both tested API36 AVDs. Artifact record: `docs/evidence/phase11/03-artifact-verification/signed-rc2-2026-10-05.md`.

## Runtime acceptance

**PASS** signed RC2 clean manual first use; separate clean cold dApp-first LOCAL Approve and Reject; funded normal memo-only Local MWA sign-and-send with independently finalized Devnet signature and persisted success session; ordinary authorization Reject with OBSERVED_PROTOCOL provenance; FAULT_SIGN_REJECT → ERROR_NOT_SIGNED (-3) with INJECTED provenance and return-to-NORMAL after restart/full AVD reboot. **PASS** Receive Test SOL UI *and independently decoded screenshot QR pixels*: 44-byte exact current public address, not an MWA or Solana Pay URI. **PASS** invalid recipient, zero, -1, 10-decimal precision and 1 SOL insufficient-reserve **live** negative-send validations; no review confirmation/submission. **PASS** one minimal one-lamport direct Devnet send to a documented user-controlled Test Wallet, finalized independently with no MWA protocol-history pollution. **PASS** Markdown, JSON, Copy Summary and Android Share Sheet on both real success and injected sessions; four actual report-byte hashes and bounded secret audit. Screenshots: 16 exact-RC2 PNGs with validated checksums. See `docs/evidence/phase11/rc2-runtime-2026-10-05.md`, `docs/evidence/phase11/07-reports/rc2-2026-10-05.md`, `docs/evidence/phase11/09-screenshots/MANIFEST.md`.

## Security and privacy

Installed release: INTERNET present, CAMERA absent, no unexpected dangerous permission, exported Local MWA association, FileProvider non-exported; no Remote scanner/mainnet/seed-import UI. Four actual report files were inspected and pattern-scanned; no raw credential assignment, PEM key or raw sensitive payload field found in the bounded files. Main/system logcat audits captured **clean first-use/cold authorization** and **funded AVD restart/Copy/negative validation** windows; neither window includes the earlier funded normal signing, injected fault, original report sharing or direct send, so full workflow logging is **not yet cleared**. Dependency verbose `MobileWalletAdapterSession` public-key logging and an auth-repository message warrant review before claiming a broad privacy PASS. `docs/evidence/phase11/08-security-privacy/`.

## Compatibility

Internal Demo Client → MWA Lab Local MWA VERIFIED / SHIPPED. Remote MWA BLOCKED / HIDDEN / NOT RELEASED; Remote scanner OMITTED. Production wallets NOT TESTED / NOT_VERIFIED. Mainnet UNAVAILABLE. `docs/evidence/phase11/12-compatibility/final-matrix.md`.

## Known defects and remaining gates

No reproducible P0/P1 app defect or approved repair. Earlier debug/emulator Launcher/System UI ANRs did not prevent signed RC2 acceptance on the healthy disposable API36 device. Remaining **security audit gap**: capture a funded-workflow logcat window covering authorization, signing, fault execution, report export and direct send without needlessly repeating live transfers, or obtain explicit scoped owner risk acceptance. Review dependency verbose logging without convenience dependency upgrades. Synthetic tests retain identity-change/RPC-failure/ambiguous-confirmation/signature-mismatch coverage; those conditions were not forced live.

## CI and tag provenance

Phase 11 branch is wired to CI; this evidence checkpoint's **exact-head GitHub Actions PASS has not yet been established**. No final Phase 11 annotated tag or post-tag receipt exists. Planned October 6 freeze date must not be imputed to October 5 evidence.

## Phase 12 handoff

Preserve immutable signed-RC2 digest and source provenance; resolve or explicitly accept the remaining scoped log/privacy review, verify exact-head CI, then decide final annotated hard-freeze tag in a separate closeout batch. This is a substantially qualified RC2 **candidate**, not a completed Phase 11 freeze; do not reopen Remote MWA or change protected source absent documented P0/P1 necessity.
