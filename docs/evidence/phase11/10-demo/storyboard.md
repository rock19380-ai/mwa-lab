# Phase 11 competition demo storyboard — 114-second assembly plan

Candidate uses **exact signed RC2** evidence only: APK SHA-256
`5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`,
artifact source `4b97796ab7958b2590d32271de8e0a0786cbc824`.
Format: 1080×2400 portrait source, silent or low/no audio, large high-contrast
English text overlays. Export a 114-second final edit only after visual QA of
the source in/out points. No new Devnet transfer is needed for this demo.

| Final time | Picture / exact source | Overlay text |
| --- | --- | --- |
| 00–07 s | `01-home-normal.mp4` 00–07 s: RC2 Home and Sessions transition | **MWA Lab** / MWA Protocol Debugger / DEVNET ONLY |
| 07–17 s | `../09-screenshots/03-cold-local-authorization.png` | Android dApp → Connect Wallet → **Local MWA** / truthful UNVERIFIED identity |
| 17–27 s | `../09-screenshots/04-normal-transaction-diagnostics.png` | Review the Devnet request before signing |
| 27–43 s | `01-home-normal.mp4` 10–26 s: persisted normal session/timeline/diagnostics | Trace → inspect → confirmed normal path |
| 43–49 s | `../09-screenshots/06-fault-sign-reject-active.png` | Fault Lab: **FAULT_SIGN_REJECT** |
| 49–69 s | `02-fault-sign-reject.mp4` 00–20 s: active fault, same Demo Client action, returned result | Repeat the same dApp request |
| 69–80 s | `../09-screenshots/07-error-not-signed-injected.png` | **ERROR_NOT_SIGNED (-3)** / **INJECTED** / exact fault ID |
| 80–96 s | `03-report-wallet.mp4` 28–44 s: sanitized export controls and Android Share Sheet | Export a sanitized Markdown/JSON report |
| 96–103 s | `03-report-wallet.mp4` 51–58 s: disposable Test Wallet balance | Test Wallet: disposable Devnet infrastructure |
| 103–108 s | `../09-screenshots/09-receive-test-sol.png` | Receive Test SOL QR = public Devnet address |
| 108–114 s | Plain dark end card; use text overlay, no product UI | **Make Mobile Wallet Adapter failures visible, reproducible, and fixable.** |

The stills are in the exact-RC2 screenshot manifest. The raw clips were recorded
on the funded `MWA_Lab_RC_API_36` AVD with the installed base.apk digest
verified equal to RC2. Clip 1 shows an already accepted normal Devnet session,
so the recording does not spend Devnet SOL for visual drama. Clip 2 is the
correct `FAULT_SIGN_REJECT` take. Clip 3 starts on the injected session and
ends after returning the fault to NORMAL and showing the Test Wallet.

Assembly notes: crop Android system bars only if the labels remain truthful;
keep full-width error/failure source text readable; use hard cuts or short
fades; add no Remote MWA visual. Review the raw clips frame by frame and adjust
source in/out points by at most a few seconds around each stated action before
publishing. Do not use the discarded temporary wrong-fault recording on the
device. Source screenshots are evidence stills, not reenactments.
