# Phase 11 predecessor verification

Verified 2026-10-05 before branching from a clean worktree.

| Check | Observed |
| --- | --- |
| Starting branch / HEAD | `phase10-release-candidate-compatibility-evidence` / `fa7a909f50ab0702327bd98f28461f60fe7ad082` |
| Starting worktree | clean (`git status --short --branch` printed only branch header) |
| Local Phase 10 tag | annotated Git `tag` object `1c4a0f243807bb491bae57828b704c633a3ba997`; peeled target `fa7a909f50ab0702327bd98f28461f60fe7ad082` |
| Origin predecessor | `refs/heads/phase10-release-candidate-compatibility-evidence` = `fa7a909f50ab0702327bd98f28461f60fe7ad082` (`git ls-remote origin`) |
| Origin tag | annotated object `1c4a0f243807bb491bae57828b704c633a3ba997`; remote peeled target = `fa7a909f50ab0702327bd98f28461f60fe7ad082` |
| Signed RC1-r2 production source | `945295a3e0124af11a5d75a76c7444f09586339e`; all protected paths identical at Phase 10 closeout (`git diff --quiet`, exit 0) |
| Phase 10 exact-HEAD CI | GitHub Actions run `37264327058`: completed/success, head SHA `fa7a909f50ab0702327bd98f28461f60fe7ad082` (queried with `gh run view`) |
| Signed RC1-r2 signer certificate | SHA-256 `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4` in tag and Phase 10 evidence; no new signing performed |

Recent ancestry, newest first: `fa7a909` → `c16ac58` → `e4b8778` → `57f44ce` → `945295a` → `361f039` → `cc6c7df` → `1e178bf` → Phase 9 `67f4b58`. The new `phase11-hard-code-freeze` branch was created directly from the annotated Phase 10 tag. Frozen predecessor history was not rewritten.

Source/config and Phase 10 evidence agree: `dev.mwalab`, versionCode `1`, versionName `0.1.0-clockin`, walletlib/clientlib `2.0.7`, Room schema `4`, fixed HTTPS Solana Devnet RPC. App and Demo Client manifests contain INTERNET and not CAMERA. The app exposes the Local MWA association handler; no Remote scan/paste control is shipped. `docs/RELEASE.md`, `PHASE_10_REPORT.md`, and funded acceptance evidence record Local MWA verified/shipped, Remote MWA blocked/hidden/not released, scanner omitted, production-wallet compatibility NOT_VERIFIED, mainnet unavailable. The Receive QR encodes an address, not an MWA association URI. Phase 10 runtime evidence belongs to the predecessor candidate; it is not Phase 11/RC2 runtime acceptance.
