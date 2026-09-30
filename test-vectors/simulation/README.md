# Phase 5 deterministic simulation fixtures

The two hex transactions are public templates with a synthetic payer
(0x11 repeated) and synthetic blockhash (0x66 repeated). They are not live
Devnet transactions and cannot establish a live PASS. The demo-client factory
substitutes its authorized public payer and a freshly fetched Devnet blockhash
for live attempts. GOOD_MEMO needs enough Devnet lamports to pay the fee;
BAD_SYSTEM_OPCODE needs the same fee precondition to reach its deterministic
runtime instruction failure.

The JSON files are synthetic RPC responses for parser and authority tests.
The free-form log sentinel in rpc-pass.json must be redacted before any
public or durable result. No fixture is evidence of a live RPC call.
Run python3 scripts/verify_phase5_simulation_vectors.py to verify templates.
