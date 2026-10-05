# Phase 10 RC1-r2 pre-freeze checkpoint — 2026-10-05

This is the committed candidate checkpoint **before** final exact-head GitHub CI
and annotated freeze tag. It deliberately does not self-reference a CI run or tag
that can exist only after this commit is created.

## Artifact

```text
MWA-Lab-v0.1.0-clockin-rc1-r2.apk
SHA-256 0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21
signer certificate SHA-256 a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
package dev.mwalab
versionCode 1
versionName 0.1.0-clockin
```

The production app code represented by this artifact is commit
`945295a3e0124af11a5d75a76c7444f09586339e`; later Phase 10 changes are tests,
evidence, and release documentation.

## Runtime gates

```text
manual first run                         PASS
cold dApp-first Local MWA               PASS
SIGN_MESSAGE_APPROVE                    PASS
canonical Local sign-and-send           PASS / FINALIZED
FAULT_SIGN_REJECT sign-and-send         PASS / ERROR_NOT_SIGNED (-3) / INJECTED
final persisted fault                   NORMAL
one direct Test Wallet send             PASS / FINALIZED
protocol-history isolation              PASS
Markdown export                         PASS
JSON export                             PASS
Copy Summary                            PASS
Android Share Sheet                     PASS
actual report-byte secret audit         PASS
restart persistence                     PASS
Remote MWA                              BLOCKED / NOT RELEASED
CAMERA                                  ABSENT
mainnet                                 UNAVAILABLE
production-wallet compatibility         NOT_VERIFIED
live airdrop invoked                    NO
known P0                                0 at this checkpoint
known P1                                0 at this checkpoint
```

Detailed receipts live in sibling Phase 10 evidence directories.

## Final provenance still required

- final candidate local/static/build gates;
- clean-checkout verification;
- push exact candidate branch;
- exact-head GitHub Actions `success`;
- annotated tag `phase10-release-candidate-compatibility-evidence-2026-10-05`;
- remote tag-target verification;
- external final freeze receipt containing run ID/URL, exact HEAD, tag target,
  and artifact digests.
