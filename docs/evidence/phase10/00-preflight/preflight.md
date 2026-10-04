# Phase 10 preflight — 2026-10-04

Commands ran before any edits on `phase9-first-run-connection-ux` in
`/home/abbaas/mwa-lab`. Initial `git status --short --branch` showed only
`## phase9-first-run-connection-ux...origin/phase9-first-run-connection-ux`;
the worktree was clean. `git rev-parse HEAD` returned
`67f4b583ae1f7446c21a092c6345d45e9b258a9c`.

`git cat-file -t phase9-first-run-connection-ux-2026-10-04` returned `tag`,
and `git rev-list -n1 phase9-first-run-connection-ux-2026-10-04` returned
`67f4b583ae1f7446c21a092c6345d45e9b258a9c`. `git show-ref --tags`
listed both the Phase 8 and Phase 9 tags; `git log --oneline --decorate -12`
showed the Phase 8 commit `7cb9c0d` preceding Phase 9 commits. The
Phase 8 tag is an ancestor of HEAD (`git merge-base --is-ancestor`: exit 0).
The annotated Phase 9 tag message also records the same frozen commit.

Direct `gh api repos/rock19380-ai/mwa-lab/actions/runs/37179611544` returned
`status=completed`, `conclusion=success`, `head_branch=phase9-first-run-connection-ux`,
and `head_sha=67f4b583ae1f7446c21a092c6345d45e9b258a9c`.

Source inspection before edits:

- `app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt`: `version = 4`;
  exported `app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json` exists.
- `gradle/libs.versions.toml`: `mwaWalletlib = "2.0.7"`; walletlib and
  clientlib both refer to that version key.
- `app/build.gradle.kts`: application ID `dev.mwalab`, versionCode `1`,
  original versionName `1.0`, compileSdk `37`, targetSdk `37`, minSdk `23`.
- `app/src/main/AndroidManifest.xml`: INTERNET permission and exported
  `solana-wallet` association handler present; CAMERA permission absent.
- `app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt`: fixed
  `https://api.devnet.solana.com` authority.
- `scripts/phase9_static.sh`: PASS before edits. It checks mainnet absence,
  scanner dependency/controls absence, direct Send recorder isolation,
  address-only Receive QR, and release-status caveats. The Phase 9 tag records
  Local MWA `PASS`, Remote MWA `BLOCKED_HIDDEN`, Remote QR scanner `OMITTED`,
  and production-wallet compatibility `NOT_VERIFIED`; those latter results
  are predecessor evidence, not new Phase 10 live validation.

Toolchain observed: `java -version` OpenJDK `21.0.12.1` (Gradle daemon toolchain
requests Java 25); `./gradlew --version` Gradle `9.6.0`, Kotlin `2.3.21`,
launcher JVM `21.0.12.1`; `adb version` `1.0.41`, installed platform tools
`37.0.1-15733141`. `local.properties` SDK path:
`/home/abbaas/Android/Sdk`; installed build-tools: `36.0.0`; installed
platforms: `android-36`, `android-37.0`.

Frozen baseline: `./scripts/phase9_static.sh` passed, then
`./gradlew lint test assembleDebug :app:assembleDebugAndroidTest
:demo-client:assembleDebugAndroidTest` exited 0, `BUILD SUCCESSFUL in 3m 18s`
(165 tasks: 10 executed, 155 up-to-date). No live Devnet operation or airdrop
was invoked. After baseline, `git status --short --branch` remained clean.
Only then was `phase10-release-candidate-compatibility-evidence` created from
the exact frozen HEAD; `git merge-base --is-ancestor` of Phase 9 into its
new HEAD returned exit 0.
