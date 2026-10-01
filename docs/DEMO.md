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
