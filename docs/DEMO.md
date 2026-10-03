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
