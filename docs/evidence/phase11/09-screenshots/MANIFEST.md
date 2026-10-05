# Exact signed-RC2 screenshots — captured October 5, 2026

All 16 rows: signed APK SHA-256 `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`, artifact source HEAD `4b97796ab7958b2590d32271de8e0a0786cbc824`, Android 16/API36/x86_64. Serial `emulator-5554` represents **two AVDs at different times**: F = funded `MWA_Lab_RC_API_36` with RC2 installed in place; C = freshly installed `MWA_Lab_Phase11_Probe_API_36`. Demo Client screenshots reflect interactions with the exact signed wallet, not a debug wallet. PNG hashes were recomputed and all matched this manifest.

| Screenshot | Device | Screenshot SHA-256 | Actual proof |
| --- | --- | --- | --- |
| `00-clean-first-run.png` | C | `0eb28f9aa042e6e45763d34e9af41e5bb96067a0432cfbd219257cfb2712d310` | Fresh first-use, Devnet and no-real-funds warning |
| `01-clean-home.png` | C | `0e134d3a5044a15e095e3488c64ce92d443e662ea496942d73954499218d5ee3` | Home after first-run onboarding |
| `01-home.png` | F | `298770a7a9de2c888e83e9510d4495804d486c0b03d338c959e55172299d8b5a` | Funded upgraded release home |
| `02-how-to-connect.png` | F | `63f70093d17aee28568017babe0ea35365707046b494dc46762a7d0e8f8ad8d0` | Same-device guidance, Devnet |
| `03-cold-local-authorization.png` | C | `5dba4662b01eaa37a8d8b9edf0ac2ae365b65445f983fda78aaa6d798d030214` | Genuine dApp-first cold-launch LOCAL approval UI |
| `03-cold-approve-demo.png` | C | `3a4ab5a555bbe029542a7998e2f7ddce9069a131b4895a1fc06520edbc653962` | Demo Client first-use authorization PASS |
| `03-local-authorization-upgrade.png` | F | `71b644ee5a821dffc65ff29c17f6a1aa92771d858cc4363c2cf43aa5470ab96c` | LOCAL consent and truthful unverified identity |
| `03-authorization-rejected-demo.png` | F | `78918934c1658ff151d182d68353b2bc31e9eb2a632f3a2bd8107efa73942c54` | Result following real authorization Reject |
| `03-local-authorized-demo.png` | F | `adaa02844b04a6fcd1e4cd986ae0869c56120859f52c462964c66ca25bb87d0d` | Canonical authorization and capabilities |
| `04-normal-transaction-diagnostics.png` | F | `c5ee8a3422189d98fb0f1d759b6bafff979a7b0125e9dfcd6d47dbca00ad6c40` | Memo transaction review before real approval |
| `05-normal-session-timeline.png` | F | `5eedf6e7571362f0ddeef3c6ec19cd7cbc981a2b0f53ea396997d8594c083c09` | Persisted LOCAL success; failure source NONE |
| `06-fault-sign-reject-active.png` | F | `89c099006b01e8cdc6443977f393f330d03c64ca3d46bbe9ff2464e4e496dca3` | Intentional signing rejection warning |
| `07-error-not-signed-injected.png` | F | `c816d6494a62bff9bf3600397583f808d42644a931f21d9613502d164d3ae34a` | Failure -3, INJECTED, exact fault ID |
| `08-report-share.png` | F | `0dc458d3498c88afe3d3088d4783590c9e3e532e800ebde1065746a64ee301b9` | Android Share Sheet; nothing transmitted |
| `09-receive-test-sol.png` | F | `0ecdfb1ae60dabc9a61098b2f84fdd6031693459437572ba636a462cfd029171` | Current address QR; pixels independently decoded to public address only |
| `10-send-test-sol-review.png` | F | `5231b569217ae04d83745e05a8760b3f380d374b758f0bb19baffd0ab7ba42d4` | One-lamport direct-send review, Devnet |

No screenshot implies Remote MWA ships. See `../06-test-wallet/receive.md` for independent QR pixel-decode result and `../08-security-privacy/secret-audit.md` for remaining funded-workflow logcat coverage limits.
