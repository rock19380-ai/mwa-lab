# Phase 6 live NORMAL-mode Devnet regression

Date: 2026-10-01T14:46:21+06:30
Branch: `phase6-deterministic-fault-engine`
Prefreeze candidate HEAD: `5fa788b5c3ef4e5e3cc57d69decad2489d437863`
Device/emulator serial: `emulator-5554`
Device model: `sdk_gphone64_x86_64`
Android: `16`
Build fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`

The real demo-client Phase 2 `SIGN_AND_SEND_APPROVE` scenario was launched with MWA Lab in NORMAL mode.
The MWA Lab screen remained explicitly Devnet/test-only, the approval was performed through the normal user-visible
approval path, and the demo client reached:

`PHASE2 SIGN_AND_SEND_APPROVE: PASS`

This confirms that the Phase 6 fault engine leaves the existing NORMAL-mode Devnet sign-and-send path operational.
No mainnet or production funds were used. This evidence does not claim production-wallet compatibility.
