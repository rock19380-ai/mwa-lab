#!/usr/bin/env python3
"""Static security scan for Phase 5 diagnostic persistence and authority boundaries."""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
issues = []

def require(ok, msg):
    if not ok:
        issues.append(msg)

def read(rel):
    return (ROOT / rel).read_text()

entity = read('app/src/main/java/dev/mwalab/storage/simulation/SimulationResultEntity.kt').lower()
for forbidden in ('rawtransaction', 'raw_transaction', 'transactionbytes', 'transaction_bytes',
                  'signature:', 'authtoken', 'auth_token', 'associationtoken', 'association_token',
                  'privatekey', 'private_key', 'seed:', 'rpcbody', 'rpc_body'):
    require(forbidden not in entity, 'unsafe simulation entity field/token: ' + forbidden)

limits = read('app/src/main/java/dev/mwalab/simulation/SimulationLimits.kt')
for token in ('MAX_LOG_LINES', 'MAX_LOG_LINE_CODE_POINTS', 'MAX_LOG_TOTAL_CODE_POINTS', 'REDACTED_LOG', 'publicLogLine'):
    require(token in limits, 'missing log safety control: ' + token)

coordinator = read('app/src/main/java/dev/mwalab/simulation/TransactionSimulationCoordinator.kt')
require('transaction.copyOf()' in coordinator, 'coordinator does not own transient copy')
require('.bytes.fill(0)' in coordinator or 'owned.fill(0)' in coordinator, 'transient byte clearing missing')
for forbidden in ('LabSigningService', 'ApprovalCoordinator', 'sendTransaction(', 'awaitCommitment('):
    require(forbidden not in coordinator, 'coordinator authority leak: ' + forbidden)

settlement = read('app/src/main/java/dev/mwalab/simulation/SimulationDiagnosticSettlement.kt')
require('simulations.recordForEvent' in settlement, 'simulation child persistence missing')
require('recorder.complete' not in settlement, 'simulation settlement completes protocol')

rpc = read('app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt')
require('DEVNET_RPC_URL = "https://api.devnet.solana.com"' in rpc, 'fixed Devnet URL missing')
require('.put("sigVerify", false)' in rpc, 'sigVerify=false missing')
require('.put("replaceRecentBlockhash", false)' in rpc, 'replaceRecentBlockhash=false missing')

# Detect direct logging of obviously sensitive variable names in runtime Kotlin.
log_pattern = re.compile(r'\b(?:Log\.[vdiew]|println|print)\s*\([^\n]*(?:authToken|associationToken|privateKey|seed|signedTransaction|transactionBytes|rawTransaction)', re.I)
for path in (ROOT / 'app/src/main').rglob('*.kt'):
    source = path.read_text(errors='ignore')
    if log_pattern.search(source):
        issues.append('possible sensitive logging: ' + str(path.relative_to(ROOT)))

schema = json.loads((ROOT / 'app/schemas/dev.mwalab.storage.MwaLabDatabase/3.json').read_text())
sim = next((e for e in schema['database']['entities'] if e.get('tableName') == 'simulation_results'), None)
require(sim is not None, 'schema 3 simulation_results missing')
if sim:
    cols = {f['columnName'].lower() for f in sim.get('fields', [])}
    for forbidden in ('raw_transaction', 'transaction_bytes', 'signed_transaction', 'signature', 'auth_token',
                      'association_token', 'private_key', 'seed', 'rpc_request', 'rpc_response'):
        require(forbidden not in cols, 'unsafe schema column: ' + forbidden)

if issues:
    print('PHASE 5 SECURITY SCAN: FAIL')
    for issue in issues:
        print('- ' + issue)
    sys.exit(1)
print('PHASE 5 SECURITY SCAN: PASS')
