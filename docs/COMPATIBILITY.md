# Compatibility

Only directly observed compatibility evidence belongs here. Phase 9 path
states are `PASS`, `FAIL`, `PARTIAL`, `NOT TESTED`, and `BLOCKED`. They
describe a tested path, device, date, and evidence receipt, not a general wallet
endorsement.

Phase 8 status: **NOT VERIFIED IN THIS RELEASE**. Its Android emulator run used
MWA Lab's own test endpoint; no production-wallet path was tested. See
[Phase 8 compatibility smoke](evidence/phase8/phase8-production-wallet-smoke.md).

Phase 9 production-wallet compatibility: **NOT_VERIFIED**. The internal Demo
Client is a deterministic test dApp, not a production wallet test. Its
current-run Local NORMAL memo sign-and-send and injected signing rejection
both passed over a real cross-package association. This is internal test
endpoint evidence only. Remote MWA is `BLOCKED / NOT RELEASED`; its scanner is
`OMITTED / NOT APPLICABLE`. See
[Phase 9 audit](evidence/phase9/09-adversarial-audit/live-and-security.md).
