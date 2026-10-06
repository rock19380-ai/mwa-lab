# Compatibility

Only directly observed compatibility evidence belongs here. Allowed states are
`PASS`, `FAIL`, `PARTIAL`, `NOT TESTED`, and `BLOCKED`.

## Phase 11 signed-RC2 release matrix

Exact signed RC2 SHA-256:
`5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`.
See [the evidence matrix](evidence/phase11/12-compatibility/final-matrix.md).

| Endpoint / wallet | Device | Local MWA | Remote MWA | Evidence state |
| --- | --- | --- | --- | --- |
| MWA Lab Demo Client → MWA Lab RC2 | Android 16 / API 36 AVD | **PASS**: cold dApp-first and funded memo sign-and-send | **BLOCKED / NOT RELEASED** | Internal deterministic test dApp only |
| Phantom | — | **NOT TESTED** | **NOT TESTED** | **NOT_VERIFIED** |
| Solflare | — | **NOT TESTED** | **NOT TESTED** | **NOT_VERIFIED** |
| Seed Vault Wallet | — | **NOT TESTED** | **NOT TESTED** | **NOT_VERIFIED** |
| Other production wallets | — | **NOT TESTED** | **NOT TESTED** | **NOT_VERIFIED** |

The internal Demo Client does not establish production-wallet compatibility.

```text
Same-device Local MWA     SHIPPED / VERIFIED
Remote MWA                BLOCKED / HIDDEN / NOT RELEASED
Receive Test SOL QR       SHIPPED / PUBLIC DEVNET ADDRESS ONLY
Solana Pay QR             DIFFERENT PROTOCOL; NOT AN MWA CONNECTION
Mainnet                    UNAVAILABLE
```

Historical Phase 10 compatibility evidence remains under
`docs/evidence/phase10/`; it is not relabeled as RC2 testing.
