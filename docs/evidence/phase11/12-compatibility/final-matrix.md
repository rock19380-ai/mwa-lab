# Release compatibility matrix — October 5 checkpoint

| Surface | Evidence | Status |
| --- | --- | --- |
| Internal Demo Client → MWA Lab Local MWA, signed Phase 10 RC1-r2 | Phase 10 funded acceptance and tag; not re-tested on RC2 | VERIFIED on Phase 10; RC2 PENDING |
| Debug-only cross-app probe on disposable API 36 AVD | Two authorization timeouts with Android launcher/System UI ANRs | UNRESOLVED / environment-blocked; not an RC2 verdict |
| Remote MWA transport and QR scanner | Not shipped; Phase 11 static guard enforces no production controls | BLOCKED / NOT RELEASED; scanner OMITTED |
| Phantom, Solflare, Seed Vault and other production wallets | No direct release-specific test | NOT TESTED / NOT_VERIFIED |
| Mainnet | Fixed Devnet-only authority | UNAVAILABLE |

No production-wallet claim follows from the internal Demo Client or from synthetic debug instrumentation. This matrix remains provisional until exact signed-RC2 runtime acceptance.
