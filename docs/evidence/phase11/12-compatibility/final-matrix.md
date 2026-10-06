# RC2 compatibility matrix — October 6, 2026

Exact signed APK SHA-256
`5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`;
artifact source `4b97796ab7958b2590d32271de8e0a0786cbc824`.
The final bounded logcat audit is
[`08-security-privacy/final-bounded-logcat-audit-2026-10-06.md`](../08-security-privacy/final-bounded-logcat-audit-2026-10-06.md).

| Surface | Exact RC2 evidence | Release status |
| --- | --- | --- |
| Internal Demo Client → MWA Lab Local MWA | Cold dApp-first Approve/Reject; funded memo sign-and-send; persisted sessions; independent Devnet finalization | **PASS / VERIFIED / SHIPPED** |
| Remote MWA / QR camera scanning | No released transport, scanner, control or CAMERA permission; static/manifest/UI checks | **BLOCKED / HIDDEN / NOT RELEASED**; scanner **OMITTED** |
| Phantom | No direct RC2 test | **NOT TESTED / NOT_VERIFIED** |
| Solflare | No direct RC2 test | **NOT TESTED / NOT_VERIFIED** |
| Seed Vault Wallet | No direct RC2 test | **NOT TESTED / NOT_VERIFIED** |
| Other production wallets | No direct RC2 test | **NOT TESTED / NOT_VERIFIED** |
| Mainnet | Fixed Devnet-only release, no mainnet path | **UNAVAILABLE** |

The internal Demo Client is a test dApp, not a production wallet. Its PASS does
not establish compatibility with any production wallet. The earlier debug-only
cross-app ANR did not reproduce on clean signed-RC2 dApp-first acceptance; it
remains a separate emulator/test-environment observation. Receive Test SOL QR is
a public Devnet address, not Remote MWA or Solana Pay connection input.
