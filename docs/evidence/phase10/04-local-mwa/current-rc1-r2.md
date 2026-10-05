# Current RC1-r2 Local MWA boundary — 2026-10-05

On dedicated `MWA_Lab_RC_API_36`, signed revision-2 dApp-first Local MWA
authorization PASS and explicit rejection FAIL were observed through real
user-visible wallet controls; see `../03-clean-install/current-rc1-r2.md`.
No approval coordinator or database was bypassed.

The current identity then had **0 SOL** by read-only Devnet query. Under the
operator's absolute no-airdrop/funding rule, no later sign-message, Demo
Client RPC, sign-and-send, or fault transaction acceptance was attempted on
this final state. The October 4 `UnknownHostException` remains historical and
**unresolved for this new AVD**, not a wallet signing failure. The prior
one-off Demo Client ANR remains historical; canonical transaction flow has
not yet run under normal conditions on this acceptance AVD. Resume only after
user funding and first re-read this AVD's current installed public address.
