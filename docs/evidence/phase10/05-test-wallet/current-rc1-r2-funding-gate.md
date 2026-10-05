# Current signed-RC funding gate — 2026-10-05

After the final clean dApp-first install, `dev.mwalab` on
`MWA_Lab_RC_API_36` reports versionName `0.1.0-clockin` and the Test Wallet
screen independently matches the onboarding address:

`FLAU1DewjS2jNpmf5eAWjC8h1BjQvzs8JNrDCBX2P8ja`

Read-only command:

`solana balance FLAU1DewjS2jNpmf5eAWjC8h1BjQvzs8JNrDCBX2P8ja --url https://api.devnet.solana.com`

Result: **0 SOL**. No airdrop/faucet/UI funding action and no live transaction
was invoked. This identity is insufficient for canonical sign-and-send and
one direct Test Wallet Send. **WAITING_FOR_USER_DEVNET_SOL**. The user must
transfer Devnet SOL manually to this exact currently installed address.

On resume, re-read the installed signed-RC address and query balance again.
If the address has changed, do not use this stale one: report the new exact
address and wait for that identity to be funded. Do not run destructive
connected tests, uninstall the app, clear data, or reset the test identity.
