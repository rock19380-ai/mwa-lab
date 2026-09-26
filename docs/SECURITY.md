# Security and Diagnostic Invariants

## S1 — Devnet-only execution boundary

Mainnet signing/submission must not be available.

Unsupported production chains must fail closed.

## S2 — No production secrets

Do not support mnemonic import, production private-key import, Seed Vault
extraction, or production-wallet migration.

## S3 — Secrets never enter diagnostics

Never persist or export:

- private keys;
- seeds;
- raw authorization tokens;
- encryption keys;
- sensitive wallet credential material.

## S4 — Sanitized logging

Prefer structured metadata such as:

- SHA-256 payload fingerprint;
- payload length;
- transaction/message version;
- verified public metadata.

Raw transaction/message payloads are not exported by default.

## S5 — Explicit lab identity

Signing and approval surfaces must clearly communicate:

MWA LAB TEST ENDPOINT<br>
SOLANA DEVNET<br>
NO REAL FUNDS

## S6 — User-visible signing

Normal mode must not silently sign.

## S7 — Fail closed

Malformed input must produce a defined failure.

Parse failure must never become approval.

## S8 — Diagnostic truthfulness

Unknown data remains unknown.

Never invent program names, amounts, token symbols, instruction semantics,
wallet capabilities, or causes of failure.

## S9 — Failure-source integrity

Failures must be classified explicitly:

- NONE
- INJECTED
- OBSERVED_PROTOCOL
- SIMULATION
- RPC_NETWORK
- LOCAL_PARSER
- UNKNOWN

Synthetic and naturally observed failures must never be conflated.
