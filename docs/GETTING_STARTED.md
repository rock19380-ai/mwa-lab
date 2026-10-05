# Getting started with MWA Lab

MWA Lab is a **Devnet-only Mobile Wallet Adapter protocol debugger and failure
simulator**. Its disposable Test Wallet exists to exercise MWA flows. Never send
mainnet assets or production-wallet secrets to MWA Lab.

## Same-device Local MWA — normal starting path

1. Install MWA Lab on the Android device/emulator.
2. Open an Android dApp that uses Mobile Wallet Adapter.
3. Tap **Connect Wallet** in the dApp.
4. Choose **MWA Lab** if Android shows a wallet chooser.
5. Review the request in MWA Lab. The screen identifies the connection as
   same-device MWA and Solana Devnet.
6. Approve or reject explicitly.
7. Run the dApp operation.
8. Open **Sessions** in MWA Lab to inspect the persisted protocol trace,
   transaction diagnostics, simulation evidence where present, fault provenance,
   and report actions.

You do **not** need to open MWA Lab first or start a server. A cold Local MWA
association can launch MWA Lab directly.

## Manual first launch

Manual launch explains the product before any protocol session is waiting. The
app creates/loads a disposable Devnet test identity and shows:

- public address;
- Devnet balance;
- same-device connection instructions;
- current fault state;
- recent session information.

There is no seed-phrase ceremony, production-wallet import, or mainnet selector.

## Funding the Test Wallet

Use **Receive Test SOL** to copy the current disposable public address or display
its address QR. The QR is for **Devnet funding only** and is explicitly not an
MWA connection QR.

`Request Devnet SOL` is a convenience feature whose external faucet/RPC may be
rate-limited or unavailable. Faucet availability is not required for MWA Lab to
work. A developer may instead fund the current address from another Devnet wallet
or Solana CLI.

Always re-read the current address after reinstalling, clearing application data,
or resetting the identity. Those actions can replace the disposable identity.

## Send Test SOL

**Send Test SOL** supports one native Devnet SOL recipient and one amount. It:

- validates a canonical 32-byte Solana address;
- requires a positive amount with at most 9 decimals;
- preserves a fee reserve;
- presents a Devnet review screen;
- uses preflight and confirmed commitment;
- reports ambiguous post-submission state as submitted/unknown rather than
  fabricated success.

Direct Test Wallet actions are local utilities. They are **not** dApp→wallet MWA
requests and therefore do not create protocol-session events.

## Fault Lab

Select one deterministic fault profile, then repeat the same dApp request.
Injected failures are visibly marked as intentional and persisted separately from
the terminal failure source. Return the selection to **NORMAL** after testing.

The competition acceptance path verifies `FAULT_SIGN_REJECT` returning
`ERROR_NOT_SIGNED (-3)` / `INJECTED` without transaction submission.

## Reports

Session Detail can:

- Share Markdown;
- Share JSON;
- Copy Summary.

Reports are derived from persisted structured evidence. They exclude private
keys, seeds, raw authorization/association tokens, raw transaction/message
payloads, raw signatures, and release-signing secrets.

## Remote MWA

Remote MWA is **not released in this RC**. There is no Remote QR scanner, no
paste-Remote-URI control, and no camera permission. Do not confuse:

```text
Receive Test SOL QR   = disposable Devnet public address
Remote MWA QR         = protocol association (not released here)
Solana Pay QR         = separate payment protocol
```

## Troubleshooting

### MWA Lab does not appear in the dApp wallet flow

- confirm MWA Lab is installed;
- confirm the dApp actually uses Mobile Wallet Adapter;
- retry the dApp's Connect Wallet flow;
- verify the requested network/chain is supported (Devnet only).

### A Devnet operation cannot proceed

- verify the **current installed** Test Wallet address;
- refresh/query its Devnet balance;
- fund that exact address if required;
- do not infer success from a prior identity or old installation.

### Demo Client networking

The internal Demo Client is test infrastructure. On the dedicated Phase 10 AVD,
Android background-network policy initially blocked its Java RPC path; the
acceptance environment applied a Demo Client-only device-idle allowlist without
changing the wallet's HTTPS RPC endpoint, TLS, or product authority. This is an
environment caveat, not a wallet protocol result.
