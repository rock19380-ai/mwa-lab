# Release-runtime security boundary — October 4–5, 2026

The **external signed RC1 APK**, SHA-256
`a48372f422ddae239d698062db77601b6d06651ba135ef8eb8dc43146bb519ec`,
verified with `apksigner` (v1/v2), package `dev.mwalab`, versionCode 1,
versionName `0.1.0-clockin`, public signer certificate SHA-256
`a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4`.
`aapt dump permissions` showed INTERNET but no CAMERA. The APK manifest has
the exported `solana-wallet` association activity and a non-exported
FileProvider. The Phase 10 static gate passes for fixed Devnet RPC, absent
mainnet authority, absent Remote scanner/dependency/controls, and direct
Test Wallet Send not routing through ProtocolRecorder. Remote MWA remains
`BLOCKED_HIDDEN`, scanner `OMITTED`, production-wallet compatibility
`NOT_VERIFIED`. Runtime first run showed disposable Test Wallet, DEVNET ONLY,
NO REAL FUNDS, and no production-wallet import ceremony.

The Demo Client's extra RPC diagnostic displays only an I/O exception **class**
under a fixed message; no exception message, transport payload, token,
keystore, or password is rendered. No raw logcat or raw authorization/share
payload was stored in this evidence tree. Prior unit/instrumentation
sanitization tests passed; **full exported-content and runtime-log secret
audits remain pending**. October 5 emulator version/identity mismatch prevents
additional signed-RC runtime claims; see
`../03-clean-install/resume-device-mismatch.md`.
