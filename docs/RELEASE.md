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


## Phase 9 pre-freeze state (2026-10-04)

Phase 9 is `BLOCKED_EXTERNAL_FUNDING`, not a release candidate. The installed
disposable Test Wallet address is
`B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG` and its confirmed Devnet
balance was 0 lamports. The interrupted live run had used a prior disposable
address before the connected-suite uninstall/reinstall. The opt-in live Send and canonical NORMAL Local
sign-and-send could not submit. The injected Local rejection passed and its
Room evidence was checked. Remote MWA remains `BLOCKED_HIDDEN`; its scanner is
`OMITTED`; production-wallet compatibility is `NOT_VERIFIED`.

Fund that public address with at least 100,000 Devnet lamports, then rerun the
two opt-in live submission gates and verify confirmation plus Room evidence.
Do not create a Phase 9 freeze tag until the mandatory live gates and
exact-head CI succeed. See [Phase 9 report](../PHASE_9_REPORT.md).
