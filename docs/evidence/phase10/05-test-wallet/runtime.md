# Test Wallet acceptance boundary — October 4–5, 2026

The signed RC displayed the disposable Devnet public address
`8AgfEQ8YZ9bpWe1rS1iaAEeTDJt5aFs3K9PwuuQhY3nY`. The visible COPY ADDRESS
control copied it; pasting into the Send recipient field reproduced the exact
same text. The Receive UI identified its QR as a public-address funding QR,
**NOT an MWA connection QR**, and warned against mainnet funds. The QR's encoded
bytes were **not independently decoded**, so only UI/source equivalence is
claimed. The host read-only Devnet `getBalance` returned 300000000 lamports
(0.3 SOL). Request Devnet SOL was visible but **never invoked**: the user
supplies Devnet SOL manually. Do not treat this operator prohibition as removal
of the shipped feature.

`TestSolTransferTest` provides deterministic no-spend checks for invalid
recipient, zero/negative amounts, more than nine decimal places, amount above
balance plus fee reserve, preflight enabled with confirmed commitment, and
ambiguous confirmation/signature mismatch remaining `SubmittedUnknown`.
`TestWalletSendService.submit` rechecks the signing identity before fetching
a blockhash; RPC blockhash failure returns without signing or submitting.
Existing ordinary connected report/wallet suites ran before RC installation.
These tests are **not** a signed-RC positive live Send acceptance.
On October 5, `./gradlew :app:testDebugUnitTest
:demo-client:testDebugUnitTest` completed successfully without touching the
device; prior connected regression results are recorded separately.

There is **no new signed-RC direct Send transaction signature**, no proven
post-send balance refresh, and no before/after direct-Send MWA session count.
Do not invent a recipient or use the old Phase 9 wallet identity as one.
October 5 resumption discovered `emulator-5554` had reverted to a different
installed app/version and address (see
`../03-clean-install/resume-device-mismatch.md`); stop instead of sending from
or funding a stale wallet identity.
