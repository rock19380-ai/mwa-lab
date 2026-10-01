#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
P=ROOT/'docs/phase6-deterministic-fault-engine-design.md'
assert P.is_file(), 'Phase 6 design missing'
s=P.read_text()
ids=['NORMAL','FAULT_AUTH_REJECT','FAULT_SIGN_REJECT','FAULT_DELAY_5S','FAULT_UNSUPPORTED_CHAIN',
     'FAULT_INVALID_PAYLOAD','FAULT_TOO_MANY_PAYLOADS','FAULT_RPC_UNAVAILABLE','FAULT_SUBMISSION_FAILURE','FAULT_STALE_BLOCKHASH']
for x in ids: assert x in s, f'design missing {x}'
for x in ['AUTHORIZATION_DECISION','SIGNING_VALIDATION','SIGNING_PRE_APPROVAL','TRANSACTION_BLOCKHASH_CHECK','SUBMISSION_PRE_RPC']:
    assert x in s, f'design missing hook {x}'
for phrase in ['Room schema 3','walletlib 2.0.7','request','snapshot','INJECTED','OBSERVED_PROTOCOL','Phase 7']:
    assert phrase.lower() in s.lower(), f'design missing contract phrase: {phrase}'
print('PHASE 6 DESIGN CHECK: PASS')
