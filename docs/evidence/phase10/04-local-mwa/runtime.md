# Local MWA runtime evidence — October 4, 2026

These are observations on the signed RC1 before the October 5 device-state
regression recorded in `../03-clean-install/resume-device-mismatch.md`.

- Cold dApp-first Local MWA: real Android association and wallet APPROVE
  returned Demo Client Phase 1 PASS. The separate real wallet REJECT returned
  authorization failure `ERROR_AUTHORIZATION_FAILED (-1)` with persisted
  `OBSERVED_PROTOCOL`, not an injected fault. Association mode `LOCAL`, dApp
  identity `UNVERIFIED`, Devnet; the persisted authorized session had an
  `AUTHORIZE` SUCCESS event with failure source `NONE`.
- The intentional `FAULT_SIGN_REJECT` profile was selected in the real Fault
  Lab: `FAULT ACTIVE`, `INTENTIONAL TEST CONDITION`, expected
  `ERROR_NOT_SIGNED (-3)`. Demo Client Phase 6 `SIGN_REJECT` requested
  **SIGN_MESSAGE** through real Local MWA after visible user authorization;
  it returned `CLIENT RESULT VERIFIED`, received protocol error `-3`.
  The RC persisted a LOCAL failed session, 3 events, failed method
  `SIGN_MESSAGES`, `ERROR_NOT_SIGNED (-3)`, failure source `INJECTED`, and
  `FAULT_SIGN_REJECT`. This operation has **no Devnet transaction submission**.
  The visible RETURN TO NORMAL control was tapped, and Fault Lab showed
  `NORMAL · no intentional fault selected` afterward.
- The RC-specific **canonical sign-and-send is NOT proven**. Demo Client
  Phase 2 `SIGN_AND_SEND_APPROVE` stopped before signing approval at
  `Devnet RPC I/O failure (UnknownHostException)` while fetching the
  blockhash. A sign-transaction-only diagnostic reproduced the same pre-sign
  Demo Client failure. Host read-only RPC and Android shell ping worked, but
  toggling emulator Wi-Fi and private DNS did not resolve the Java failure.
  This is a Demo Client networking blocker, **not a wallet signing failure**.
  No RC-specific transaction signature or confirmation was obtained.
- A single Demo Client ANR was observed during repeated DNS diagnosis while
  the host was under memory/Gradle-daemon pressure. After stopping Gradle
  daemons, the sign-only attempt returned `UnknownHostException` instead;
  no repeatable app ANR is established. Do not dismiss this as a resolved
  release risk until the normal RC transaction path succeeds on a stable
  device.
- A non-submitting `SIGN_MESSAGE_APPROVE` run was started at the previous
  session boundary but no result was captured. The October 5 emulator has
  reverted; **do not claim this run passed**.

The Demo Client now exposes only a sanitized `Devnet RPC I/O failure
(UnknownHostException)` class when its fixed-endpoint RPC fails; it never
renders the exception message, request body, credentials, or authorization
data. Screenshots `../09-screenshots/06-cold-dapp-first-authorization.png`,
`07-local-mwa-reject.png`, `10-persisted-authorization-timeline.png`,
`14-sign-reject-active.png`, `15-sign-reject-demo-result.png`,
`16-persisted-sign-reject.png`, and `19-restored-normal.png` capture UI states.
