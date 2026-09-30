# Independent live audit method and scope

The real cross-package runner executed all three scenarios using the installed
production composition and fixed https://api.devnet.solana.com RPC. Evidence
contains only safe UI and structured results. No mock transport was selected.

The runner receipt alone is preliminary. Independent `adb exec-out run-as
 dev.mwalab sqlite3 -readonly -json databases/mwa_lab.db` queries joined
`simulation_results`, `protocol_events` and `transaction_diagnostics` by exact
session/event/payload/fingerprint. For each of the three distinct fingerprints:

- one legacy PARSED transaction and one attempt (payload index 0, attempt 1)
  matched the parent request summary and simulation target;
- PASS/NONE or FAIL/SIMULATION matched the visible UI;
- the two approvals had SUCCESS/NONE parents; rejection had FAILURE,
  OBSERVED_PROTOCOL and -3, while the simulation remained PASS/NONE;
- ordered sanitized logs met 64-line, 512-code-point and total limits;
- the session closed after AUTHORIZE, SIGN_TRANSACTIONS and DEAUTHORIZE;
- schema version, quick_check and foreign_key_check passed.

Before/after snapshots were compared in memory across a real `am force-stop`
and launcher restart. Process disappearance and a changed PID were checked.
Every selected session, canonical event, full safe transaction summary and
simulation row remained equal. The structured receipt stores a digest and safe
metadata, not the snapshots. The last session was opened from Home after restart;
its parent error, Phase 4 transaction, fingerprint-bound child, expanded redacted
logs and warning were observed in the actual persisted UI.

The DB, WAL and SHM were read through run-as into bounded host memory (8 MiB
limit per file). All three fingerprint byte strings were found. Each unsigned
transaction was reconstructed transiently from the existing public template and
safe recorded payer/blockhash metadata, and its SHA-256 was required to equal
the recorded fingerprint before use as a scan probe. Exact raw, hex and base64
unsigned forms and the message body/encoded suffix that a signed transaction
would contain were absent. The acceptance memo, raw JSON-RPC envelope markers,
known synthetic secret sentinels and unsafe simulation columns were absent.
Only filenames, byte counts and boolean results were retained. DB/WAL/SHM bytes,
reconstructed payloads and raw RPC bodies were not copied into evidence.

This scan did not extract real individual signatures, authorization/association
tokens, private keys or encryption keys to create probes. It cannot prove the
absence of an unknown literal. Existing instrumented synthetic-sentinel tests,
strict storage schemas, sanitized JSON/logs and source authority checks remain
separate evidence for those boundaries. The actual live DB check is distinct
from the earlier disposable Room test database scan.

The first temporary host audit attempt encountered non-JSON command output
during relaunch; the strict repeat completed with all comparisons passing.
A parent failure-source UI assertion initially ran before that field was in the
viewport; scrolling to it verified the actual value. Neither issue changed
product behavior or relaxed the acceptance assertions.
