#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$(pwd)}"
cd "$ROOT"
bash ./scripts/phase1_static.sh "$ROOT"
# The frozen Phase 2 gate intentionally excludes Room. Preserve its bytes and
# verify the unchanged predecessor sources without executing that scope gate.
sha256sum --check docs/evidence/phase3/phase3-phase2-protected-sources.sha256
python3 - <<'CHECK'
from pathlib import Path
import json, re

def require(condition, message):
    if not condition:
        raise SystemExit("PHASE 3 STATIC GATE ERROR: " + message)

base=Path('app/src/main/java/dev/mwalab')
versions=Path('gradle/libs.versions.toml').read_text()
for key,value in {'agp':'9.4.1','kotlin':'2.2.10','ksp':'2.3.12','room':'2.8.5','mwaWalletlib':'2.0.7'}.items():
    require(f'{key} = "{value}"' in versions, 'Pinned toolchain changed: '+key)
require('gradle-9.6.0-bin.zip' in Path('gradle/wrapper/gradle-wrapper.properties').read_text(), 'Gradle pin changed')
for flag in ('android.disallowKotlinSourceSets=false','android.builtInKotlin=false','android.newDsl=false'):
    require(flag not in Path('gradle.properties').read_text(), 'Forbidden compatibility escape hatch')

db=json.loads(Path('app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json').read_text())['database']
require(db['version']==1, 'Schema version is not 1')
entities={e['tableName']: e for e in db['entities']}
require(set(entities)=={'sessions','protocol_events'}, 'Unexpected diagnostic tables')
expected={'sessions':{'session_id','started_at_ms','completed_at_ms','dapp_identity_name','cluster','close_reason'},
'protocol_events':{'event_id','session_id','sequence','method','started_at_ms','completed_at_ms','outcome','protocol_error_code','failure_source','injected_fault_id','request_summary_json','response_summary_json','capability_context_json'}}
for table,cols in expected.items():
    require({f['columnName'] for f in entities[table]['fields']}==cols, 'Unexpected/secret-bearing schema columns')
events=entities['protocol_events']
require(any(i['unique'] and i['columnNames']==['session_id','sequence'] for i in events['indices']), 'Missing session/sequence uniqueness')
require(events['foreignKeys']==[{'table':'sessions','onDelete':'CASCADE','onUpdate':'NO ACTION','columns':['session_id'],'referencedColumns':['session_id']}], 'Foreign key changed')
require('DATABASE_NAME = "mwa_lab.db"' in (base/'storage/MwaLabDatabase.kt').read_text(), 'Database name changed')
require('exportSchema = true' in (base/'storage/MwaLabDatabase.kt').read_text(), 'Schema export disabled')
for file in base.rglob('*.kt'):
    source=file.read_text()
    require('fallbackToDestructiveMigration' not in source, 'Destructive migration fallback')
    if file.name!='NetworkPolicy.kt':
        require(not re.search(r'api\.mainnet-beta\.solana\.com|solana:mainnet|mainnet-beta',source), 'Mainnet implementation')
    require(not re.search(r'(Log\.(?:d|i|v|w|e)|println\().*(authToken|auth_token|privateKey|mnemonic|payload)',source), 'Sensitive ad-hoc log')

host=(base/'mwa/MwaSessionHost.kt').read_text()
require(not re.search(r'Room\.|SessionDao|ProtocolEventDao|SQLite',host), 'Host queries storage directly')
require(not re.search(r'method\s*=\s*ProtocolMethod\.GET_CAPABILITIES',host), 'Synthetic capabilities event')
for anchor in ('protocolRecorder.begin(', 'protocolRecorder.complete(', 'persistentSessionId',
    'AUTH_ISSUER_NAME = "mwa-lab-phase1"', 'approvalCoordinator.requestApproval',
    'activeAuthorizationGeneration', 'isCurrentGeneration(generation)', 'invalidateAuthorization(generation)',
    'LegacyTransactionCodec.parseForSigner', 'rpcGateway.isBlockhashValid',
    'SignAndSendSubmissionExecutor(rpcGateway).execute', 'isRequestCurrent = { isAuthorizationActive(generation) }',
    'request.completeWithReauthorize()', 'request.completeWithSignedPayloads',
    'request.completeWithNotSubmitted', 'request.completeWithSignatures',
    'MAX_PERSISTED_PAYLOAD_METADATA = 10'):
    require(anchor in host, 'Missing predecessor/recorder boundary: '+anchor)
for method in ('AUTHORIZE','REAUTHORIZE','DEAUTHORIZE','SIGN_MESSAGES','SIGN_TRANSACTIONS','SIGN_AND_SEND_TRANSACTIONS'):
    require('ProtocolMethod.'+method in host, 'Missing observed method: '+method)
recorder=(base/'protocol/recorder/PersistentProtocolRecorder.kt').read_text()
for anchor in ('pendingByEventId','settlingByEventId','completedByEventId','closedSessionSummaries',
    'safeSummary(responseSummary)', 'DiagnosticSanitizer.sanitizeFields', 'withContext(NonCancellable)',
    'injectedFaultId = null','capabilityContext = null','method != ProtocolMethod.GET_CAPABILITIES'):
    require(anchor in recorder, 'Missing recorder invariant: '+anchor)
for file in ('HomeViewModel.kt','SessionsViewModel.kt','SessionDetailViewModel.kt','SessionsScreen.kt','SessionDetailScreen.kt'):
    require((base/'ui/sessions'/file).is_file(), 'Missing product state/UI: '+file)
for file in (base/'ui/sessions').glob('*.kt'):
    source=file.read_text()
    require(not re.search(r'ProtocolEvidenceStore|MwaSessionEvidenceStore|Dao|SQLite|Room\.',source), 'UI bypasses repository')
require('collectAsStateWithLifecycle' in (base/'MainActivity.kt').read_text(), 'UI collection lacks lifecycle boundary')
require('NOT OBSERVABLE THROUGH PINNED WALLETLIB' in (base/'ui/sessions/SessionDetailScreen.kt').read_text(), 'Capability limitation hidden')
require(Path('PHASE_3_REPORT.md').is_file(), 'Phase 3 report missing')
require(not (base/'ui/transaction').exists(), 'Phase 4 transaction inspector introduced')
print('PHASE 3 STATIC / PERSISTENCE / PREDECESSOR GATE: PASS')
CHECK
