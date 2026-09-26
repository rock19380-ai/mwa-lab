# Threat Model

Initial threats include:

- mainnet request reaching a signing path;
- malformed MWA payloads;
- stale or invalid authorization state;
- secret leakage into logs or reports;
- injected failures being mistaken for real-wallet failures;
- incorrect transaction semantic decoding;
- RPC/network failure being misclassified as protocol failure;
- accidental silent signing.

Controls are defined in `SECURITY.md`.
