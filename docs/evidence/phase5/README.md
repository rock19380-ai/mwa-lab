# Phase 5 evidence

This directory contains Phase 5 simulation/diagnostic evidence. Evidence files
must never contain private keys, seeds, raw authorization/association tokens, raw
transaction payloads, raw signatures, or raw RPC request/response bodies.

The initial baseline/design evidence belongs to Steps 5.0–5.4. Later local gate,
security-scan, hostile-audit, database-byte-scan, device, live-Devnet, and CI
receipts are added only when those checks actually run. Synthetic fixtures under
`test-vectors/simulation/` are deterministic test inputs and are not live Devnet
receipts.
