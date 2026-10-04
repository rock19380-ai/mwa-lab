# Phase 10 release configuration — 2026-10-04

The app preserves application ID `dev.mwalab`, versionCode `1`, walletlib and
clientlib `2.0.7`, and Room schema `4`. Its versionName is now
`0.1.0-clockin`. The planned externally named signed APK is
`MWA-Lab-v0.1.0-clockin-rc1.apk`; the `rc1` suffix is not in app metadata.

Signing uses four environment variables: `MWALAB_RELEASE_STORE_FILE`
(existing absolute keystore path outside repository),
`MWALAB_RELEASE_STORE_PASSWORD`, `MWALAB_RELEASE_KEY_ALIAS`, and
`MWALAB_RELEASE_KEY_PASSWORD`. Partial signing configuration fails at Gradle
configuration, and no secrets or keystore are tracked. With no signing
environment, Gradle builds an unsigned release APK for buildability checks;
it is not RC1. CI has no private signing credentials.

At this checkpoint none of the four variable names was present in the
operator process environment (`env` variable-name inspection only; no
values were read or printed). Signed RC1 remains pending operator secret.
Remote MWA remains blocked/hidden and production-wallet compatibility remains
not verified. No live Devnet action is part of this batch.
