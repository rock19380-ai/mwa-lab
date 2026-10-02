# Privacy

MWA Lab is a local Devnet-only protocol debugger and failure simulator. It does
not require user accounts, cloud sync, analytics tracking, or a backend.

Phase 7 reports are derived from persisted diagnostic evidence and stored only
as disposable app-private cache artifacts. Markdown, JSON, and clipboard summary
use one allowlisted report model. They may include public addresses, program IDs,
transaction SHA-256 fingerprints, timing, outcomes, capability context, and
bounded diagnostic classifications. They intentionally exclude private keys,
seeds, raw authorization/association tokens, raw transaction/message bytes, raw
signatures, raw RPC bodies, and arbitrary stack traces.

Sharing is user-initiated through Android Share Sheet with a temporary read grant
for one report file. Choosing a third-party target is the user's action; MWA Lab
does not upload reports itself. Delete cached reports through normal app cache
management or application data clearing.
