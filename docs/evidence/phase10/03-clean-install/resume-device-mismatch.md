# Resume stop: emulator reverted — October 5, 2026

Reconstructed from disk before new runtime mutations: branch
`phase10-release-candidate-compatibility-evidence`, HEAD
`cc6c7dfc1e28ae666e7955e88ea8301171cce231`; Phase 9 frozen commit remains
an ancestor, and `./scripts/phase10_static.sh` passes. Existing October 4
signed-RC and regression evidence and screenshots remain on disk. The
external signed RC1 APK still hashes to
`a48372f422ddae239d698062db77601b6d06651ba135ef8eb8dc43146bb519ec`;
`apksigner verify` succeeds with certificate SHA-256
`a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4`.

**Critical difference:** `adb -s emulator-5554 shell dumpsys package
dev.mwalab` now reports installed versionName `1.0`, first installed
`2026-10-04 09:53:54`, **not** the signed RC1 versionName `0.1.0-clockin`.
Launching this existing older app solely to read its public onboarding state
showed address `B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG`, **not** the
October 4 signed-RC identity
`8AgfEQ8YZ9bpWe1rS1iaAEeTDJt5aFs3K9PwuuQhY3nY`.
Read-only `solana balance B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG
--url https://api.devnet.solana.com` returned **0.799989999 SOL** on
October 5 at `2026-10-05T02:09:40Z`.

This is a **device/identity regression**, not evidence that the prior RC
identity was unfunded. Stop signed-RC live acceptance. No app data was cleared,
no wallet identity reset, no RC reinstall, no airdrop, no live signing or spend
was performed during resume. Do not use either stale address for a new test.
Recover the original funded signed-RC device state if possible; otherwise
establish a new signed-RC acceptance install, read its **new current** address,
and obtain user-supplied funding if needed before any live transaction.
