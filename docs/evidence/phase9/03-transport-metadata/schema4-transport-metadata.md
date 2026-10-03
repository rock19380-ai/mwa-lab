# Phase 9 — transport metadata / Room schema 4

Date: 2026-10-03

## Predecessor

- Batch 2: `8beccc3413b7bc26405c6715e6825a2ff95fba57`
- Frozen Phase 8: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`

## Delivered

- Room schema `3 -> 4`, forward-only and non-destructive.
- Session evidence persists coarse `association_mode` and
  `identity_verification_state` only.
- Historical Phase 8 sessions migrate to `LOCAL` + `NOT_AVAILABLE`.
- New Local authorization evidence is `LOCAL` + `UNVERIFIED` when a claimed
  identity is present, otherwise `NOT_AVAILABLE`.
- Domain constraints prevent Local sessions from claiming
  `REMOTE_UNVERIFIED` and prevent Remote sessions from claiming unproven
  `VERIFIED` state.
- Session list/detail and Markdown/JSON/summary reports expose coarse transport
  and verification state.
- No raw association URI, reflector ID/token, association public key, auth
  token, transport secret, seed, mnemonic, or private-key column was added.
- Diagnostic sanitizer defense rejects Remote-shaped secret field names before
  future Remote work.

## Gate repair discovered by the full connected suite

The first Batch 3 resume reached the full app connected suite after schema 4
migration, domain/report tests, targeted migration tests, and historical
persistence tests were already green.

One legacy Phase 6 fault-evidence test opened a *fresh current* Room database
and still asserted that the database version was `3`. With Phase 9 schema 4,
the authoritative current version is `4`. The repair changes only that stale
test expectation; no production Room, fault, protocol, or migration behavior is
relaxed.

## Green gates

- main/unit/android-test Kotlin compilation: PASS
- targeted session/report/sanitizer JVM tests: PASS
- schema 3->4 migration + repository + Local authorization device tests: PASS
- historical persistence device regressions against schema 4: PASS
- fault-evidence latest-schema regression: PASS
- full app connected suite: PASS
- Demo Client cross-package connected suite: PASS
- full lint/JVM/APK gate: PASS
- frozen Phase 8 static gate from detached worktree: PASS
- `git diff --check`: PASS

## Release status

Transport metadata foundation: **GREEN**.

Remote MWA itself is not claimed by this milestone. Raw Remote transport
material remains outside durable session evidence.
