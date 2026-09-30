#!/usr/bin/env python3
"""Device evidence parser must fail promptly and cannot certify Room from UI."""
from pathlib import Path
import runpy

module = runpy.run_path(str(Path(__file__).with_name('phase5_device_acceptance.py')),
                        run_name='phase5_device_acceptance_parser_test')
wait = module['wait_for_text']
xml = ('<hierarchy><node text="PHASE5 GOOD_PASS_APPROVE: FAIL"/>'
       '<node text="Failure: LAB_IDENTITY_NEEDS_DEVNET_SOL '
       'balance_lamports=0 required_fee_lamports=5000"/></hierarchy>')
wait.__globals__['dump_xml'] = lambda: xml
try:
    wait('SIMULATE', timeout=1)
except RuntimeError as exc:
    message = str(exc)
    assert 'LAB_IDENTITY_NEEDS_DEVNET_SOL' in message
    assert 'required_fee_lamports=5000' in message
    assert 'last_xml' not in message
else:
    raise AssertionError('explicit funding failure was swallowed')

summary = module['preliminary_summary']([])
assert summary['status'] == 'UI_ONLY_PENDING_DATABASE_AND_RESTART_VERIFICATION'
assert summary['databaseByteScan'] == 'NOT_RUN'
assert summary['restartPersistence'] == 'NOT_RUN'
assert summary['freezeTagCreated'] is False
assert 'rawPayloadPersisted' not in summary
print('PHASE 5 DEVICE ACCEPTANCE PARSER: PASS')
