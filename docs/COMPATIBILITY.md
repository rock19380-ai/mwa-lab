# Compatibility

Only directly observed compatibility evidence belongs here. Allowed states are
`PASS`, `FAIL`, `PARTIAL`, `NOT TESTED`, and `BLOCKED`.

## Phase 10 release matrix

| Endpoint / wallet | Version / dependency | Device | Local MWA | Remote MWA | Evidence state | Notes |
|---|---|---|---|---|---|---|
| MWA Lab Demo Client → MWA Lab RC1-r2 | walletlib/clientlib 2.0.7; app 0.1.0-clockin | Android 16 / API 36 dedicated AVD | **PASS** | **BLOCKED / NOT RELEASED** | Verified 2026-10-05 | Internal deterministic test dApp; canonical NORMAL sign-and-send, sign-message, injected sign rejection, persistence, and reports verified |
| Production wallets (Phantom / Solflare / Seed Vault Wallet / others) | — | — | **NOT TESTED** | **NOT TESTED** | **NOT_VERIFIED** | No release-specific production-wallet compatibility claim is made |

The internal Demo Client is not a production wallet and cannot establish
production-wallet compatibility by itself.

## Connection-mode truth

```text
Same-device Local MWA     SHIPPED / VERIFIED
Remote MWA                BLOCKED / NOT RELEASED
Receive Test SOL QR       SHIPPED / ADDRESS ONLY
Solana Pay QR             NOT AN MWA CONNECTION INPUT
```

Remote MWA is not hidden because the specification lacks a concept; it is hidden
because this release lacks the required end-to-end acceptance evidence. No dead
Remote button, camera permission, scanner dependency, or universal compatibility
claim ships.

Historical Phase 8/9 compatibility evidence remains under
`docs/evidence/phase8/` and `docs/evidence/phase9/`; Phase 10 evidence is under
`docs/evidence/phase10/`.
