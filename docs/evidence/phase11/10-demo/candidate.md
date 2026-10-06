# Phase 11 demo candidate — raw assets and edit plan

**Status: three raw exact-RC2 screen recordings and a 114-second assembly plan
exist; a finished edited video does not yet exist.** Source artifact:
`MWA-Lab-v0.1.0-clockin-rc2.apk`, SHA-256
`5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`;
source commit `4b97796ab7958b2590d32271de8e0a0786cbc824`.
The installed APK was independently read back and matched before recording.

| Raw file | Duration | SHA-256 | What was observed |
| --- | ---: | --- | --- |
| `01-home-normal.mp4` | 38.477 s | `bc2aa9131c2254495f0d96fe5b0b0341a31a69a2d9fec09b03b42feca1c7ac42` | Home and persisted normal Local MWA session |
| `02-fault-sign-reject.mp4` | 34.600 s | `439e8bbe50c3b76c85538913a39cbf30f4ad60cd4f5cedab6b208b1cfed98d28` | Correct active fault and Demo Client rejection |
| `03-report-wallet.mp4` | 59.604 s | `eca08c304fd265143bd51a8227d63d64edc18c5fff268a237a7fa4ababeb18b8` | Injected detail, report Share Sheet, NORMAL restoration, Test Wallet |

The MP4 `mvhd` duration/size headers and hashes were checked. UI hierarchy was
inspected during capture; frame-by-frame edited-video review remains open.
`storyboard.md` gives the exact 114-second source/caption schedule and
`recording-checklist.md` names the remaining Phase 12 edit/export checks.
The source stills are independently hash-inventoried in
`../09-screenshots/MANIFEST.md`. No Remote MWA or production-wallet
compatibility claim belongs in the edit.
