# Demo

## Canonical RC2 competition demo

Use the exact signed RC2 and the deterministic internal Demo Client on the same
Android device or acceptance emulator.

Target: **90–120 seconds**, silent or minimal background audio, with large
English captions.

### Storyboard

```text
0:00
Devnet tests the chain.
MWA Lab tests the wallet protocol.

0:07
MWA Lab Home
- MWA Protocol Debugger
- DEVNET ONLY
- NO REAL FUNDS
- Test Wallet
- same-device connection guidance

0:16
Open MWA Lab Demo Client — FOR TESTING ONLY
Start the Local MWA flow.

0:26
MWA Lab authorization
- dApp identity
- SAME-DEVICE MWA
- UNVERIFIED
- SOLANA DEVNET
Approve.

0:34
Show the persisted protocol session / timeline.

0:43
Show transaction diagnostics or simulation evidence.

0:53
Show the NORMAL happy-path result.

1:00
Open Fault Lab.
Enable FAULT_SIGN_REJECT.
Show FAULT ACTIVE / INTENTIONAL TEST CONDITION.

1:08
Repeat the same dApp action.

1:17
Show:
- ERROR_NOT_SIGNED (-3)
- Failure source: INJECTED
- Injected fault: FAULT_SIGN_REJECT

1:27
Show sanitized report actions:
- Share Markdown
- Share JSON
- Copy Summary

1:36
Briefly show the disposable Test Wallet:
- Devnet balance
- Receive Test SOL
- address QR purpose

1:44
Do not show Remote MWA as shipped.
Do not perform another live transfer for visual drama.

1:52
Make Mobile Wallet Adapter failures
visible, reproducible, and fixable.

1:58
Android / Solana Devnet / GitHub
```

## Recording rules

- Use the signed `MWA-Lab-v0.1.0-clockin-rc2.apk`.
- Keep Fault Lab in `NORMAL` before and after the recording.
- Use Local MWA only.
- Do not show a Remote MWA scanner or imply Remote MWA is released.
- Do not claim production-wallet compatibility.
- Do not show or request mainnet assets.
- Do not expose private keys, seeds, mnemonics, raw auth tokens, raw
  association tokens, or signing secrets.
- Prefer the already-verified Send Test SOL evidence instead of spending
  additional Devnet SOL during recording.
- Simulation PASS is diagnostic evidence, not a submission guarantee.

## What the demo proves

The demo is intended to communicate four product facts:

1. A same-device Android dApp can reach MWA Lab through Local MWA.
2. The resulting wallet-side protocol session is persisted and inspectable.
3. A deterministic fault can reproduce a defined MWA failure without pretending
   it happened organically.
4. The resulting evidence can be exported in sanitized form.

The Test Wallet is supporting infrastructure, not the product identity.

## Verified release boundary

```text
Local MWA                       VERIFIED / SHIPPED
Remote MWA                      NOT RELEASED
Remote QR scanner               OMITTED
CAMERA permission               ABSENT
Mainnet / testnet               UNAVAILABLE / REJECTED
Production-wallet compatibility NOT VERIFIED
```

The included Demo Client is deterministic test infrastructure and does not
establish compatibility with Phantom, Solflare, Seed Vault Wallet, or another
production wallet.

## Evidence

The exact RC2 engineering evidence is under:

- `docs/evidence/phase11/`
- `docs/evidence/phase11/10-demo/`
- `PHASE_11_REPORT.md`

Phase 12 final QA also re-verified Local MWA, fault rejection, sanitized report
sharing, Receive Test SOL QR semantics, and a finalized direct Devnet Test
Wallet send without changing protected production source.

## Historical demo notes

Older phase-specific demo evidence remains under the corresponding
`docs/evidence/phase*/` directories. Those records are implementation history,
not the recommended competition recording sequence.
