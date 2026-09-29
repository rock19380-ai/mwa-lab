# Phase 4 deterministic transaction vectors

These are unsigned, public test inputs for the diagnostic parser and decoder registry.
Each `.hex` file contains lowercase serialized-wire hexadecimal followed by one newline.
The 64-byte signature slot is all zeroes. The repeated-byte account patterns represent
public addresses only; there are no private keys, seeds, authorization tokens, or production secrets.

[expected.properties](expected.properties) freezes the independently specified structural
and decoded expectations, including complete SHA-256 fingerprints, byte lengths, account
flags, program IDs, instruction references, instruction-data hashes, and precise failure offsets.
Hashes identify MWA Lab diagnostic payloads; they are not transaction signatures,
transaction IDs, or Solana transaction hashes.

| Vector | Bytes | Version / inspection | Expected diagnostics |
| --- | ---: | --- | --- |
| [legacy Memo](legacy-memo.hex) | 203 | LEGACY / PARSED | Memo Program; DISPLAYABLE status; 34 data bytes and SHA-256. Memo text is never retained in a durable summary. |
| [legacy System Transfer](legacy-system-transfer.hex) | 215 | LEGACY / PARSED | System Program Transfer, payer → destination, exactly 10,000,000 lamports (0.01 Devnet SOL). |
| [legacy unknown program](legacy-unknown-program.hex) | 206 | LEGACY / PARSED | Unknown Program; references #0/#1; 3 instruction-data bytes and SHA-256; no invented operation. |
| [legacy SPL Transfer](legacy-spl-transfer.hex) | 245 | LEGACY / PARSED | Source #1, destination #2, authority #0; exact raw amount 1,000,000. |
| [legacy SPL TransferChecked](legacy-spl-transfer-checked.hex) | 279 | LEGACY / PARSED | Source #1, mint #2, destination #3, authority #0; raw amount 1,000,000; declared wire decimals 6. |
| [malformed program index](malformed-program-index.hex) | 215 | LEGACY / MALFORMED | Program index 9 is outside three static accounts; PROGRAM_INDEX_OUT_OF_RANGE at byte 199. Unverified structure is absent. |
| [truncated instruction data](truncated-instruction-data.hex) | 214 | LEGACY / MALFORMED | System Transfer missing its final data byte; TRUNCATED_INSTRUCTION_DATA at byte 203. Unverified structure is absent. |
| [v0 unresolved lookup](v0-unresolved-lookup.hex) | 252 | V0 / PARTIAL | Three static accounts, four wire-declared slots, System Program #2, unresolved reference #3. UNAVAILABLE / UNRESOLVED_ACCOUNTS; no fabricated loaded key or Transfer semantics. |

All vectors declare one required signer. Static account #0 is the writable signer and
fee payer; the last static account is a read-only unsigned program account. Intermediate
static accounts are writable unsigned accounts. The fixed blockhash is the base58 encoding
of 32 public bytes `0x66`; it is structural fixture data, not a live/fresh Devnet blockhash.

Public address roles:

- payer: 32 bytes `0x11`
- destination / SPL source: 32 bytes `0x22`
- SPL destination / mint: 32 bytes `0x33`
- SPL checked destination: 32 bytes `0x44`
- unknown program: 32 bytes `0x55`
- v0 lookup table descriptor: 32 bytes `0x66`, writable lookup index 7, no resolved address

The untouched LegacyTransactionCodec can prepare the five well-formed legacy inputs
for the fixture public signer. It rejects the malformed/truncated inputs and rejects
v0 with VERSIONED_UNSUPPORTED. Diagnostic success never grants signing eligibility.
Signature authenticity, fresh blockhash validation, account ownership, balances, mint
metadata, and on-chain execution are outside these static fixture claims.

Regeneration and checks:

```sh
python3 scripts/generate_phase4_transaction_vectors.py --check
# Intentional regeneration only:
python3 scripts/generate_phase4_transaction_vectors.py --write
./gradlew :app:testDebugUnitTest --tests 'dev.mwalab.transaction.Phase4TransactionVectorsTest'
./gradlew :demo-client:testDebugUnitTest
```

The generator derives expectations from the hand-specified framing and role assignments;
it never invokes the application parser to generate expected output. Both modules expose
these files only as JVM test resources. They are not packaged as product transaction history.

The separate Phase4TransactionFactory substitutes the actually authorized Lab public payer
and a fresh Devnet blockhash into the System/Unknown/v0 framing. Its unit test proves byte
parity before substitution and proves that only the payer and blockhash locations change.
The frozen Phase2AcceptanceRunner, DemoLegacyTransactionFactory, and sign-and-send paths
remain unchanged.

Real device acceptance:

```sh
./gradlew :app:installDebug :demo-client:installDebug
python3 scripts/phase4_diagnostic_acceptance.py --serial emulator-5554 --evidence-dir docs/evidence/phase4
```

The automation launches the Demo Client with `mwa_phase4_scenario` set to SYSTEM_TRANSFER,
UNKNOWN_PROGRAM, or V0_REJECT. It checks the actual approval UI before approving legacy
requests, verifies the client result, matches session/event/payload identity and original
fingerprints in Room v2, then force-stops/restarts MWA Lab and verifies all four tables
and the reloaded inspector. v0 must fail signing without reaching approval.
No signAndSendTransactions, funding, simulation, faults, or product report export is used.
A device and live Devnet RPC are required; unavailable prerequisites cannot produce PASS evidence.
