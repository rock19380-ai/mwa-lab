# Phase 3 Persistence Gate Reconciliation

Phase 2 is frozen at:

```text
009a849441b9d4db453b0162ae1fd9f5fdad8dc4
```

The historical `scripts/phase2_static.sh` intentionally fails if Room/SQLite
persistence appears under `app/src/main` or `demo-client/src/main`. That absence
assertion was a **Phase 2 scope-freeze assertion**, not a product invariant for
later phases.

Phase 3 therefore does not weaken, edit, or delete the Phase 2 script. Its exact
SHA-256 remains:

```text
cece909c93555038d8a7798678f2345d7ca9c6c8e6f1347f2f684e27804c5464
```

For the Phase 3 persistence step, predecessor protection is reconciled as follows:

1. `scripts/phase2_static.sh` remains byte-for-byte frozen.
2. `MwaSessionHost.kt` remains byte-for-byte at the frozen Phase 2 version during
   the persistence-only step.
3. walletlib/clientlib remain pinned to `2.0.7`.
4. Phase 1 static verification still runs on the current tree.
5. full lint/unit/build/AndroidTest compilation still runs on the current tree.
6. Phase 3 persistence verification adds explicit checks for Room schema,
   no destructive migration fallback, Devnet-only boundaries, and no secret-bearing
   database columns.

Once the later ProtocolRecorder wiring intentionally changes `MwaSessionHost`,
Phase 3's own regression gate becomes the current-tree authority while the frozen
Phase 2 script remains historical evidence of the Phase 2 boundary.

This reconciliation does not permit changes to Phase 2 authorization, signing,
submission, error mapping, Devnet-only policy, or walletlib version semantics.

## Final Phase 3 plan reconciliation

The external detailed plan's section 32 asks Phase 3 static verification to call
Phase 2 static first. This conflicts with that frozen script's intentional Room
absence and old callback-signature assertions. The explicit continuation request
requires its exact contents remain unchanged. The narrow resolution is to run
Phase 1, verify the historical script hash and 28 untouched Phase 2 source
boundaries, then validate Phase 3 schema/recorder/UI scope and run current
behavioral/device regressions. No historical acceptance result is reclassified.
CI selects the historical gate only when the exported Phase 3 schema is absent.

Detailed plan numbering and its condensed checkpoints differ; this continuation
completed remaining product outcomes without restarting verified steps 3.0–3.6.
The master plan's eventual hero-screen examples include synthetic faults,
simulation, transaction interpretation, and export. Its explicit Phase 3 scope
and detailed exclusions defer those to their later phases. Phase 4 is not started.
