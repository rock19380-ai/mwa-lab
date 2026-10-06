# Clean remote clone submission simulation — October 6, 2026

## Genuine remote-clone checkpoint

The GitHub branch `phase11-hard-code-freeze` was first published at the
existing signed-RC2 runtime checkpoint `b0b075101c2f06e3e3c48c66e33ef436bc9b3bbb`.
A **fresh remote clone**, not a linked worktree or copied local tree, was made
with `git clone --single-branch --branch phase11-hard-code-freeze
--filter=blob:none https://github.com/rock19380-ai/mwa-lab.git
/tmp/mwa-lab-phase11-remote-clean-2026-10-06`.
It checked out that exact SHA. The Phase 10 annotated predecessor resolved to
`fa7a909f50ab0702327bd98f28461f60fe7ad082`.
Before building, the clone had no `local.properties`, no `app/build`, and a
clean worktree. The source remained clean afterward; `git ls-files --others
--exclude-standard` returned nothing. No untracked source, local patch,
keystore, signing password, or local server was copied or started.

| Check at cloned `b0b0751` | Observed result |
| --- | --- |
| `./scripts/phase11_static.sh` | **PASS**: Phase 9, Phase 10, six Phase 11 policy tests, Phase 11 guard |
| `./gradlew lint test assembleDebug assembleRelease :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --console=plain` | **PASS**, `BUILD SUCCESSFUL in 7m 10s`; 254 actionable tasks, **254 executed** |
| Output APKs | app and Demo Client debug, app unsigned release, and both debug AndroidTest APKs exist |
| Worktree | clean after build; only ignored build/cache artifacts |
| Primary release server dependency | none: Local MWA is same-device; the app uses the fixed HTTPS Solana Devnet RPC; no local backend is required |
| Signed artifact | external operator RC2 SHA-256 `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`; `apksigner` verifies v1/v2 and expected certificate SHA-256 `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4`; `aapt` shows `dev.mwalab` v1/`0.1.0-clockin` |
| Minimal device smoke | installed only the **clone-built Demo Client debug APK**; preserved the signed RC2 wallet. Canonical Phase 1 Local MWA authorization/capabilities/deauthorization returned PASS and persisted LOCAL PASS session `28bdc232-ef57-4d98-8e9d-4d571c297647`. A sanitized Markdown report opened the Android Share Sheet; no target selected. Installed wallet `base.apk` still hashed to exact RC2 afterward. |

The system Android SDK and shared Gradle dependency cache were available.
This verifies a fresh repository checkout/build, **not** fresh-network Gradle
dependency retrieval or release signing. Existing compiler deprecation and
prebuilt native-strip warnings were non-blocking.

## Current documentation candidate remote clone

After the documentation/evidence candidate `097a3b0ba02cb9dc06d617b29df201453c1bcfed`
was pushed, a second fresh clone was made from the same GitHub branch at
`/tmp/mwa-lab-phase11-final-fast-clone-2026-10-06`. To avoid repeating a slow
download, `git clone` used the main working tree as an object cache with
`--reference-if-able` and `--dissociate`; its resulting object store has **no
alternate** pointer to the main working tree. The clone's `origin` is the
GitHub repository, `HEAD` and the independently queried remote branch both
resolved to `097a3b0ba02cb9dc06d617b29df201453c1bcfed`, and the checkout
was clean with no `local.properties` or build outputs before the run.

| Check at cloned `097a3b0` | Observed result |
| --- | --- |
| Phase 11 static gate and Phase 0–11 shell syntax | **PASS** |
| Current README, changelog, release, demo, compatibility, testing, Phase 11 report, demo candidate and deck candidate relative links | **PASS**: 9 files checked, 0 missing relative links |
| `./gradlew lint test assembleDebug assembleRelease :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --console=plain` | **PASS**, `BUILD SUCCESSFUL in 4m 22s`; 254 actionable tasks, **254 executed** |
| Build outputs | app and Demo Client debug, app unsigned release, and both debug AndroidTest APKs present |
| Worktree/untracked source | clean after build; `git ls-files --others --exclude-standard` empty |
| Signed RC2 external check | SHA-256 still `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`; `apksigner` verified the expected certificate; `aapt` confirmed `dev.mwalab` 1/`0.1.0-clockin` |

The original remote clone above supplies the on-device Local MWA/report smoke;
the second clone rechecks the later current-release documentation and clean
buildability. Both used the existing Android SDK and Gradle dependency cache;
neither demonstrates fresh-network dependency resolution. The only next
repository change is this evidence correction. It creates a new candidate
HEAD, so all mandatory exact-head local gates, remote SHA matching, and CI
must be repeated for that final SHA before tagging. The CI/tag identifiers
are intentionally kept in the external post-tag receipt.
