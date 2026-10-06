# Demo

Canonical story:

1. run a Devnet action from the sample dApp;
2. connect to MWA Lab;
3. observe protocol trace and capabilities;
4. inspect/simulate where relevant;
5. complete the happy path;
6. enable one deterministic fault;
7. repeat the same action;
8. observe the exact protocol error and failure source;
9. export a sanitized diagnostic report.

<!-- PHASE6:DEMO:BEGIN -->
## Phase 6 demo flow

1. Open MWA Lab → Fault Lab and select one deterministic profile.
2. Return to the sample/demo dApp and repeat the same action.
3. Observe the returned MWA error or injected delay.
4. Open Session Detail and show the persisted injected condition independently from the terminal failure source.
5. Return Fault Lab to NORMAL and repeat the happy path.

The production demo client never silently configures wallet fault state.
<!-- PHASE6:DEMO:END -->

## Phase 7 report demo

1. Leave Fault Lab at NORMAL and run the demo client's supported Devnet
   sign-and-send action. Approve in MWA Lab and verify the client result.
2. Force-stop/reopen MWA Lab, open that historical session, and use EXPORT REPORT
   to Share Markdown, Share JSON, and Copy Summary.
3. Select FAULT_SIGN_REJECT, repeat the same sign-and-send operation, and verify
   `ERROR_NOT_SIGNED (-3)` plus `INJECTED / FAULT_SIGN_REJECT` in the persisted
   session and both exports. The UI labels it an intentional test condition.
4. Return Fault Lab to NORMAL.

Generated reports are sanitized. Simulation results, when present, are
diagnostic evidence only, never submission guarantees. This Devnet test path does
not establish production-wallet compatibility.

## Phase 8 canonical competition flow

1. Open MWA Lab and use Home to identify the Mobile Wallet Adapter protocol
   debugger, `DEVNET ONLY`, `NO REAL FUNDS`, selected fault, and last session.
2. Keep Fault Lab on NORMAL. From the installed MWA Lab Demo Client, launch the
   established `SIGN_AND_SEND_APPROVE` Devnet scenario. Inspect the wallet's
   visible transaction diagnostics and tap APPROVE. The client must report PASS.
3. Reopen the resulting persisted PASS session after app restart. Inspect the
   timeline, then use Share Markdown, Share JSON, and Copy Summary.
4. Select `FAULT_SIGN_REJECT` in Fault Lab. Confirm `FAULT ACTIVE` and
   `INTENTIONAL TEST CONDITION`, then launch `SIGN_AND_SEND_REJECT` from the
   same demo client. The client must receive `ERROR_NOT_SIGNED` without an
   approval tap. Session Detail must show `INJECTED` and the independent fault
   ID. Exercise the same three sanitized report actions.
5. Return Fault Lab to NORMAL and confirm the persisted selection.

Use only a funded **Devnet test identity** for NORMAL sign-and-send. A simulation
PASS is diagnostic evidence, not a submission guarantee. The actual final
results, including one transient `RPC_NETWORK` attempt followed by a successful
retry, are recorded in [Phase 8 evidence](evidence/phase8/). The six real
screenshot candidates are in [screenshots/phase8](../screenshots/phase8/).
Production-wallet compatibility is **NOT VERIFIED IN THIS RELEASE**.

## Phase 10 RC1 competition flow

Use the signed `0.1.0-clockin` RC and the internal Demo Client on the same Android
device/emulator. Remote MWA is not part of this demo.

1. Show Home: **MWA Protocol Debugger**, `DEVNET ONLY`, `NO REAL FUNDS`, Test
   Wallet, and same-device connection guidance.
2. Open the Demo Client and start the NORMAL Local MWA sign-and-send scenario.
3. Show explicit authorization, transaction diagnostics, and separate signing
   approval. Complete the action and show the persisted successful protocol
   session.
4. Open Fault Lab, select `FAULT_SIGN_REJECT`, and show `FAULT ACTIVE` /
   intentional test condition.
5. Repeat sign-and-send. Show client `ERROR_NOT_SIGNED (-3)` and persisted
   `INJECTED / FAULT_SIGN_REJECT` with no transaction submission.
6. Open Session Detail and demonstrate Share Markdown, Share JSON, and Copy
   Summary.
7. Return Fault Lab to `NORMAL`.
8. Briefly show Test Wallet balance and **Receive Test SOL**; optionally show the
   already-verified **Send Test SOL** review rather than spending additional
   Devnet SOL for the recording.
9. End on: **Make Mobile Wallet Adapter failures visible, reproducible, and
   fixable.**

Release evidence also proves one finalized direct 1-lamport Test Wallet Send did
not create MWA protocol history. No production-wallet compatibility claim follows
from the Demo Client.


## Phase 11 RC2 competition candidate

Use the exact signed RC2 and the internal Demo Client on one Android device.
The target is a 90–120 second silent recording with large English captions.
Show Home, Local MWA authorization, the persisted successful memo-only
sign-and-send timeline, one `FAULT_SIGN_REJECT` rerun with
`ERROR_NOT_SIGNED (-3)` / `INJECTED`, a sanitized report share sheet, and a
brief disposable Test Wallet balance/Receive Test SOL shot. Restore NORMAL.
End with “Make Mobile Wallet Adapter failures visible, reproducible, and
fixable.” Remote MWA, mainnet and production-wallet compatibility are not
presented as shipped or verified. Use existing signed-RC2 transaction evidence;
no new Devnet transfer is needed for visual drama.

Raw exact-RC2 screen recordings, the selected screenshots, storyboard and
assembly instructions are under [Phase 11 demo evidence](evidence/phase11/10-demo/).
