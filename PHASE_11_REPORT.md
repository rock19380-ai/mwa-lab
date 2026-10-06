# Phase 11 — hard-code-freeze closeout candidate

This document records the committed engineering/runtime basis for the final
Phase 11 candidate. Exact-head GitHub Actions success, the annotated freeze
tag and the external receipt are established **after** this documentation is
committed; no future SHA/run/tag is presented here as already verified.

## Predecessor and source boundary

- Phase 10 annotated predecessor
  `phase10-release-candidate-compatibility-evidence-2026-10-05` resolves to
  `fa7a909f50ab0702327bd98f28461f60fe7ad082`.
- Phase 11 branch: `phase11-hard-code-freeze`; this final closeout batch
  began at `b0b075101c2f06e3e3c48c66e33ef436bc9b3bbb`.
- The accepted Phase 10 signed production-source baseline is
  `945295a3e0124af11a5d75a76c7444f09586339e`.
  Protected `app/src/main/**`, `demo-client/src/main/**`,
  `app/build.gradle.kts`, `gradle/libs.versions.toml`,
  `settings.gradle.kts` and `gradle.properties` have **no drift** from
  that baseline. Phase 11 added no product feature.
- Approved P0/P1 repair list: **none**. The repair ledger has only its header.
  No production repair, dependency upgrade, rebuild or re-sign was needed.

## Signed RC2 identity

The exact externally stored, operator-signed artifact is
`/home/abbaas/Downloads/MWA_LAB_RC2_2026-10-05/MWA-Lab-v0.1.0-clockin-rc2.apk`,
signed from Phase 11 source commit
`4b97796ab7958b2590d32271de8e0a0786cbc824`.

| Property | Verified value |
| --- | --- |
| APK SHA-256 | `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc` |
| Signer certificate SHA-256 | `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4` |
| Android package | `dev.mwalab` |
| versionCode / versionName | `1` / `0.1.0-clockin` |
| minSdk / targetSdk | 23 / 37 |
| Install | exact installed `base.apk` hash matched RC2 on the acceptance AVDs and again during the October 6 audit/clean-clone smoke |

`apksigner` verifies the v1/v2 signature; the certificate matches Phase 10.
`aapt` confirms package/version and INTERNET/ACCESS_NETWORK_STATE with CAMERA
absent. Local MWA association is exported for Android discovery;
the diagnostic FileProvider is not exported. Remote scanner/mainnet controls
are absent. See `docs/evidence/phase11/03-artifact-verification/`.

## Exact-RC2 runtime acceptance

- **Manual first run:** PASS on freshly installed disposable
  `MWA_Lab_Phase11_Probe_API_36`, Android 16/API36/x86_64.
  Home showed MWA Protocol Debugger, Devnet-only/no-real-funds warnings,
  a disposable Test Wallet and same-device connection instructions.
- **Cold dApp-first Local MWA:** PASS on separate clean reinstalls before any
  manual wallet launch. The Demo Client cold-launched the wallet; both
  authorization Approve and Reject produced the expected real protocol
  outcome, with LOCAL/Devnet/UNVERIFIED identity truth.
- **Funded normal path:** PASS on preserved
  `MWA_Lab_RC_API_36`. A memo-only Demo Client
  `SIGN_AND_SEND_APPROVE` displayed informational transaction diagnostics,
  required separate authorization/signing approval, persisted LOCAL PASS
  session `222ececa-7aa4-44f2-9b5a-21fcad818a32`, and independently
  finalized Devnet signature
  `5y6jBiWZode1PQumovhkAQKs9xFKERThVpzziKU2f3BBY7FdgHn2ry2EwGPf2tt85A58pSv6zWwa2795hSz29c9b`.
  The October 6 bounded-audit rerun also returned Demo PASS.
- **Ordinary authorization Reject:** PASS; session
  `1ca5a4a1-6a6c-4f58-8736-b6128d9127ae` shows
  `ERROR_AUTHORIZATION_FAILED (-1)` and `OBSERVED_PROTOCOL`.
- **Deterministic signing Reject:** PASS with only
  `FAULT_SIGN_REJECT` active. Demo Client received
  `ERROR_NOT_SIGNED (-3)`, no returned signature/submission;
  persisted session `c180fa79-12c9-4f22-aee5-038d068ea838` records
  `INJECTED` and the exact fault ID. The October 6 audit rerun recorded
  the same outcome in session `869abe1f-a147-44f2-a31c-ac3c5b2d6d52`.
  RETURN TO NORMAL survived wallet force-stop/relaunch, a prior full AVD
  reboot, and a final post-demo relaunch.
- **Test Wallet:** Receive Test SOL showed the current public Devnet address;
  independent screenshot QR pixel decode yielded exactly the 44-byte address,
  not an MWA or Solana Pay URI. Five live negative REVIEW-only inputs
  (invalid recipient, zero, negative, excess precision, insufficient reserve)
  failed before submission. The October 5 original one-lamport direct send to
  the documented user-controlled Devnet recipient finalized and did not
  fabricate an MWA protocol event. One additional one-lamport direct send was
  necessary on October 6 solely for logcat coverage; independent
  `getTransaction` showed slot `507947763`, `finalized`, `err=null`,
  5,000-lamport fee and exactly 1 lamport to that same recipient. No mainnet
  or faucet transfer was used.
- **Reports:** Markdown, JSON, Copy Summary and Android Share Sheet passed on
  normal and injected sessions without external transmission. Four actual
  October 5 report files were retrieved, hashed, parsed and secret-scanned;
  no raw key, auth/association token, sensitive payload or signature bytes
  appeared. Their `truncated=true` warnings are retained truthfully. The
  October 6 audit reran report generation/share within its bounded log window.
- **Screenshots:** 16 exact-RC2 PNGs with checked hashes are inventoried in
  `docs/evidence/phase11/09-screenshots/MANIFEST.md`.

Full observations and chain identifiers:
`docs/evidence/phase11/rc2-runtime-2026-10-05.md`;
report-byte audit:
`docs/evidence/phase11/07-reports/rc2-2026-10-05.md`.

## Runtime security and privacy

The October 5 cleared `main`/`system` windows were partial and did not cover
the original funded signing/fault/export/direct-send actions. The **October 6
targeted rerun closed this residual gap** on the same installed signed RC2:
cleared buffers, explicit start/end, 54,944 bounded dated lines, normal memo
signing/submission, injected rejection, Markdown/JSON/Copy/Share, one direct
positive Test Wallet send, and final NORMAL persistence. Pattern and
dependency-message review found **no credential or raw sensitive payload
value**. Verbose walletlib lines carried received public ECDH handshake keys
and auth-record public metadata, not raw auth tokens; JSON-RPC logs contained
method lifecycle/IDs and the public protocol error, not bodies. This is a
bounded device/buffer/action result, not a global log claim.
See `docs/evidence/phase11/08-security-privacy/final-bounded-logcat-audit-2026-10-06.md`.

## Submission material and compatibility

A concise public asset set selects an RC2 Home hero, four support screenshots,
a Local MWA architecture SVG, the signed APK/digest and repository.
Three raw exact-RC2 screen recordings exist with a precise **114-second**
silent/captioned assembly plan. A finished edited video is **not** claimed;
the final edit and visual QA are Phase 12 packaging tasks. Seven
evidence-backed pitch-deck slides are written in
`docs/evidence/phase11/11-deck/candidate-content.md`; a rendered deck is not
claimed. See `docs/evidence/phase11/10-demo/`.

Compatibility truth: internal Demo Client ↔ MWA Lab **Local MWA PASS /
VERIFIED / SHIPPED**; Remote MWA **BLOCKED / HIDDEN / NOT RELEASED**, camera
scanner omitted; Phantom, Solflare, Seed Vault Wallet and other production
wallets **NOT TESTED / NOT_VERIFIED**; mainnet unavailable. Simulation is
diagnostic evidence, not a guarantee. The Demo Client does not prove
production-wallet interoperability.
`docs/evidence/phase11/12-compatibility/final-matrix.md`.

## Build, clean clone and remaining provenance

The October 5 Phase 11 static/lint/test/debug/unsigned-release/AndroidTest
baseline passed. A genuine fresh GitHub clone at starting checkpoint
`b0b075101c2f06e3e3c48c66e33ef436bc9b3bbb` passed Phase 11 static and
the full Gradle matrix with **254/254 tasks executed** and a clean worktree.
The clone-built Demo Client completed a minimal canonical Local MWA PASS and
a sanitized report Share Sheet against the unchanged signed RC2. No
untracked source, local server or signing secret was needed. This proves the
production-source checkpoint; current release documentation is being
corrected in this closeout commit and must receive exact-head validation.
`docs/evidence/phase11/13-clean-clone/verification.md`.

The final closeout commit must then pass Phase 11 and predecessor static gates,
shell syntax, lint, tests and all requested builds on its exact HEAD. It must
be pushed and matched to an exact-head successful GitHub Actions run **before**
an annotated Phase 11 tag is created. Those later identifiers belong in the
external receipt, not as invented future evidence in this commit.

## Known defects, limits and Phase 12 handoff

**Known P0 = 0; known P1 = 0.** No reproducible release-blocking app defect
or approved P0/P1 repair is known. An earlier emulator Launcher/System UI ANR
did not reproduce as a wallet blocker on the healthy signed-RC2 acceptance
path. Existing unit tests cover identity-change, RPC-preparation failure,
ambiguous confirmation and signature mismatch; those hostile states were not
forced live. The final logcat audit covers only its cleared `main`/`system`
window and actions. Production wallets were not directly tested. Clean-clone
buildability used the workstation's existing Android SDK and Gradle dependency
cache; it does not prove fresh-network dependency retrieval. Release signing
remains an external operator process.

After the exact-head CI-successful annotated tag exists, **Phase 12 must start
from `phase11-hard-code-freeze-2026-10-06`** and perform final QA/submission
packaging, including video/deck rendering. Do not reopen feature development,
Remote MWA, scanner, Solana Pay, mainnet, SPL-token send, backend, AI or
consumer-wallet work.
