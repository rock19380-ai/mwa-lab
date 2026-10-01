#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'test-vectors/faults/faults.properties'
assert p.is_file(), 'faults.properties missing'
props={}
for raw in p.read_text().splitlines():
    line=raw.strip()
    if not line or line.startswith('#'): continue
    assert '=' in line, f'malformed vector line: {raw}'
    k,v=line.split('=',1)
    assert k not in props, f'duplicate vector key: {k}'
    props[k]=v
required=['NORMAL','FAULT_AUTH_REJECT','FAULT_SIGN_REJECT','FAULT_DELAY_5S','FAULT_UNSUPPORTED_CHAIN',
          'FAULT_INVALID_PAYLOAD','FAULT_TOO_MANY_PAYLOADS','FAULT_RPC_UNAVAILABLE','FAULT_SUBMISSION_FAILURE','FAULT_STALE_BLOCKHASH']
profiles=[x for x in props.get('profiles','').split(',') if x]
assert profiles==required, f'profile order/coverage mismatch: {profiles}'
contracts={
 'FAULT_AUTH_REJECT':('AUTHORIZATION_DECISION','-1','INJECTED'),
 'FAULT_SIGN_REJECT':('SIGNING_PRE_APPROVAL','-3','INJECTED'),
 'FAULT_UNSUPPORTED_CHAIN':('AUTHORIZATION_DECISION','-7','INJECTED'),
 'FAULT_INVALID_PAYLOAD':('SIGNING_VALIDATION','-2','INJECTED'),
 'FAULT_TOO_MANY_PAYLOADS':('SIGNING_VALIDATION','-6','INJECTED'),
 'FAULT_RPC_UNAVAILABLE':('SUBMISSION_PRE_RPC','-4','INJECTED'),
 'FAULT_SUBMISSION_FAILURE':('SUBMISSION_PRE_RPC','-4','INJECTED'),
 'FAULT_STALE_BLOCKHASH':('TRANSACTION_BLOCKHASH_CHECK','-2','INJECTED'),
}
for pid,(hook,code,source) in contracts.items():
    assert props.get(f'{pid}.target_hook')==hook, pid
    assert props.get(f'{pid}.expected_protocol_error_code')==code, pid
    assert props.get(f'{pid}.expected_failure_source')==source, pid
    assert props.get(f'{pid}.expected_injected_fault_id')==pid, pid
    assert props.get(f'{pid}.submission_expected')=='NO', pid
    assert props.get(f'{pid}.send_transaction_expected')=='NO', pid
assert props.get('FAULT_DELAY_5S.delay_ms')=='5000'
assert props.get('FAULT_DELAY_5S.expected_outcome')=='NORMAL_FLOW'
assert props.get('FAULT_DELAY_5S.expected_failure_source')=='NORMAL_FLOW'
assert props.get('FAULT_DELAY_5S.expected_injected_fault_id')=='FAULT_DELAY_5S'
assert props.get('FAULT_RPC_UNAVAILABLE.expected_protocol_error_code')==props.get('FAULT_SUBMISSION_FAILURE.expected_protocol_error_code')=='-4'
assert props.get('FAULT_RPC_UNAVAILABLE.expected_injected_fault_id') != props.get('FAULT_SUBMISSION_FAILURE.expected_injected_fault_id')
low=p.read_text().lower()
for secret in ['private_key=','private key=','mnemonic=','seed_phrase=','auth_token=','association_token=','raw_transaction=']:
    assert secret not in low, f'unsafe vector field: {secret}'
print('PHASE 6 FAULT VECTOR CHECK: PASS')
