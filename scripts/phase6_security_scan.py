#!/usr/bin/env python3
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]

def text(rel):
    p=ROOT/rel
    assert p.is_file(), f'missing {rel}'
    return p.read_text(errors='ignore')

store=text('app/src/main/java/dev/mwalab/faults/PrivatePreferencesFaultSelectionStore.kt')
assert 'Context.MODE_PRIVATE' in store
assert 'active_fault_id' in store
manifest=text('app/src/main/AndroidManifest.xml')
assert 'SET_ACTIVE_FAULT' not in manifest and 'ACTION_SET_FAULT' not in manifest
main='\n'.join(p.read_text(errors='ignore') for p in (ROOT/'app/src/main').rglob('*') if p.is_file())
assert 'android.intent.action.SEND' not in main and 'ACTION_SEND_MULTIPLE' not in main, 'Phase 7 share path introduced early'
assert 'SET_ACTIVE_FAULT' not in main and 'ACTION_SET_FAULT' not in main, 'external fault setter detected'
network=text('app/src/main/java/dev/mwalab/security/NetworkPolicy.kt')
assert 'PRODUCTION_NOT_ALLOWED' in network
assert 'CHAIN_SOLANA_MAINNET' in network and 'Rejected' in network
for n in (1,2,3): assert (ROOT/f'app/schemas/dev.mwalab.storage.MwaLabDatabase/{n}.json').is_file()
assert not (ROOT/'app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json').exists()
# No exporter implementation before Phase 7.
for p in (ROOT/'app/src/main').rglob('*.kt'):
    assert p.name not in {'ReportExporter.kt','ReportExportService.kt'}, f'Phase 7 exporter introduced: {p}'
# Fault vectors are contract metadata only.
vec=text('test-vectors/faults/faults.properties').lower()
for forbidden in ('auth_token=','private_key=','mnemonic=','seed_phrase=','association_token=','raw_transaction='):
    assert forbidden not in vec, f'unsafe vector material: {forbidden}'
print('PHASE 6 SECURITY SCAN: PASS')
