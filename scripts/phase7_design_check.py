#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
design = (root / 'docs/phase7-sanitized-diagnostic-reports-design.md').read_text()
for phrase in (
    'structured persisted protocol/session state', 'Room schema 3', 'walletlib 2.0.7',
    'Devnet-only', 'mwa-lab-diagnostic-report', 'version `1`', 'COMPLETE', 'PARTIAL',
    'three snapshot pairs', 'injected fault ID', 'failure source', 'Markdown', 'JSON',
    'Private keys', 'raw transactions', '1 MiB', 'FileProvider',
):
    assert phrase in design, f'missing frozen decision: {phrase}'
print('PHASE 7 DESIGN CHECK: PASS')
