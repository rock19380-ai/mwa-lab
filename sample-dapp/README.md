# MWA Lab Demo Client

`demo-client/` is the deterministic Android client used to prove the Phase 1
wallet-side Mobile Wallet Adapter boundary.

## Identity

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

This module is test infrastructure. It is not the primary MWA Lab product and it
must not be represented as a production dApp.

## Canonical Phase 1 sequence

The runner performs the same real protocol sequence each time:

```text
discover MWA Lab through solana-wallet://
→ establish local MWA association/session
→ authorize on solana:devnet
→ receive the persistent Lab public account
→ request get_capabilities
→ deauthorize
→ close the local association
```

The client uses `mobile-wallet-adapter-clientlib:2.0.7`, matching the pinned
wallet-side `walletlib:2.0.7`.

The demo client is a separate Android package (`dev.mwalab.democlient`) and targets
the wallet package (`dev.mwalab`) through the real association intent path rather
than by calling wallet implementation classes.

No signing, transaction submission, auth-token display, or secret diagnostics are
part of the Phase 1 demo.
