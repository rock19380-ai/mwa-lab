# Signed RC1 first run and cold Local MWA — October 4, 2026

Acceptance device: `emulator-5554`, `MWA_Lab_API_36`, Android 16/API 36,
x86_64. The signed RC1 was installed after connected debug regressions; the
app and Demo Client connected suites had respectively 149/0 failures/0 errors
and 1/0 failures/0 errors (see `regression-separation.md`). No further
connected Gradle task was run against the funded RC environment.

On a clean signed-RC install, manual first launch showed **MWA Protocol
Debugger**, **DEVNET ONLY**, **NO REAL FUNDS**, disposable Test Wallet identity,
and the warning against real funds. The Home screen exposed How to Connect,
Test Wallet, Receive Test SOL, Send Test SOL, and Request Devnet SOL; the latter
was **not pressed**. How to Connect described SAME-DEVICE MWA. The Receive
screen showed the public address as an address/funding QR, said it is **NOT an
MWA connection QR**, and warned against mainnet funds. No import/seed
ceremony, remote scanner, dead Remote control, or camera prompt appeared.
Screenshots: `../09-screenshots/01-signed-rc-manual-onboarding.png` through
`05-receive-qr-warning.png`.

Before any funding, the app was clean-reinstalled to establish the mandatory
**dApp-first** state. Demo Client was started without manually launching the
RC; Android cold-launched the signed MWA association activity. Its real
authorization UI showed SAME-DEVICE MWA, SOLANA DEVNET, the Demo Client's
test-only identity and truthful `UNVERIFIED` verification status. Tapping the
visible APPROVE control returned to the Demo Client with Phase 1 canonical
connection, authorization, capabilities, and deauthorization PASS. A separate
session used the visible REJECT control and returned a real authorization
failure. Screenshot: `../09-screenshots/06-cold-dapp-first-authorization.png`.

The final signed-RC identity at that time was
`8AgfEQ8YZ9bpWe1rS1iaAEeTDJt5aFs3K9PwuuQhY3nY`; host read-only Devnet
RPC returned 300000000 lamports (0.3 SOL). The earlier manual-launch address
`57i9nGAi4YyLf5uKLzD4yEYgxQ97XpRuHUohZ1rLH61v` was **discarded before
funding** and must never be used as the RC acceptance identity.

**Resume warning:** The October 5 device no longer has this signed RC installed;
see `resume-device-mismatch.md`. This file records October 4 observations, not
current device acceptance.
