# RC2 compatibility matrix — October 5, 2026

Exact signed APK SHA-256 `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`; source `4b97796ab7958b2590d32271de8e0a0786cbc824`.

| Surface | Exact RC2 evidence | Release status |
| --- | --- | --- |
| Internal Demo Client → MWA Lab Local MWA | Cold dApp-first Approve/Reject, funded memo sign-and-send, persisted sessions, independent Devnet finalization | PASS / Local MWA VERIFIED / SHIPPED |
| Remote MWA / QR camera scanning | No released transport, scanner, control or CAMERA permission; static/manifest/UI checks | BLOCKED / HIDDEN / NOT RELEASED; scanner OMITTED |
| Phantom, Solflare, Seed Vault or other production wallets | No direct RC2 compatibility test | NOT TESTED / NOT_VERIFIED |
| Mainnet | Fixed Devnet-only release, no mainnet path | UNAVAILABLE |

Demo Client success is **not** evidence of production-wallet compatibility. A previous debug-only cross-app ANR did not reproduce on clean signed-RC2 dApp-first acceptance; it remains a separate emulator/test-environment observation.
