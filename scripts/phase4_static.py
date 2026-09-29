#!/usr/bin/env python3
"""Phase 4 repository contracts. Python 3.11+, standard library only.

Configuration/schema are parsed; migration SQL is executed in disposable memory.
Reviewed source hashes protect semantics beyond lexical checks. This is not a
Kotlin compiler, a behavioral test, device acceptance, or final-freeze evidence.
"""
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import sys
import tomllib
from typing import NamedTuple

PHASE3_SHA = '1de48365d68aa940dba18a8535495154eaf7c0549b64582066110242e72070e6'
V1_SHA = '1952a8bcaef8bf1c1910c0e99f1d13464aebbcb2b031376caee3130f9584874c'
BASELINE = 'e1529dcdc990f0f08b34127fd8eb953ec7877b84'
MANIFEST_SHA = '06a01fb303688d5327eaffc7ce0972495e3e4104ca11dea7215ec4672c36cbff'
MANIFEST = 'docs/evidence/phase4/phase4-protected-sources.json'
BASE = 'app/src/main/java/dev/mwalab/'
SCHEMAS = 'app/schemas/dev.mwalab.storage.MwaLabDatabase/'
DOCS = ('README.md', 'docs/ARCHITECTURE.md', 'docs/PROTOCOL_SUPPORT.md',
        'docs/SECURITY.md', 'docs/TESTING.md', 'CHANGELOG.md', 'PHASE_4_REPORT.md',
        'docs/evidence/phase4/README.md')
SUPPORT = {
    'authorize': 'observed', 'reauthorize': 'observed', 'deauthorize': 'observed',
    'sign_messages': 'observed', 'sign_transactions': 'observed + diagnostics',
    'sign_and_send_transactions': 'observed + diagnostics',
    'get_capabilities request': 'not observable through pinned walletlib',
    'configured capability profile': 'session snapshot persisted',
    'legacy inspection': 'supported', 'v0 detection': 'supported',
    'v0 full ALT resolution': 'not supported', 'v0 signing': 'not supported',
    'System Transfer': 'supported', 'Memo': 'supported',
    'SPL Token': 'narrow verified subset', 'unknown program': 'explicit unknown',
    'simulation': 'not implemented / Phase 5',
    'fault injection': 'not implemented / Phase 6',
    'report export': 'not implemented / Phase 7',
}


class GateError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise GateError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Token(NamedTuple):
    kind: str
    text: str


def kotlin_tokens(source):
    """Skip nested comments; retain whole string tokens and identifier boundaries."""
    result, i = [], 0
    while i < len(source):
        if source[i].isspace():
            i += 1
        elif source.startswith('//', i):
            end = source.find('\n', i)
            i = len(source) if end < 0 else end
        elif source.startswith('/*', i):
            depth, i = 1, i + 2
            while depth and i < len(source):
                if source.startswith('/*', i):
                    depth, i = depth + 1, i + 2
                elif source.startswith('*/', i):
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
            require(depth == 0, 'Unclosed Kotlin comment')
        elif source.startswith('"""', i):
            end = source.find('"""', i + 3)
            require(end >= 0, 'Unclosed Kotlin raw string')
            result.append(Token('string', source[i + 3:end]))
            i = end + 3
        elif source[i] in '\"\'':
            quote, start, i = source[i], i + 1, i + 1
            while i < len(source) and source[i] != quote:
                i += 2 if source[i] == '\\' else 1
            require(i < len(source), 'Unclosed Kotlin string/character')
            result.append(Token('string', source[start:i]))
            i += 1
        else:
            match = re.match(r'[A-Za-z_]\w*|\d+', source[i:])
            if match:
                result.append(Token('id', match[0]))
                i += len(match[0])
            else:
                result.append(Token('punct', source[i]))
                i += 1
    return result


def calls(tokens, name):
    for i, token in enumerate(tokens[:-1]):
        if token != Token('id', name) or tokens[i + 1].text != '(':
            continue
        depth, j = 1, i + 2
        while depth and j < len(tokens):
            if tokens[j].kind != 'string':
                depth += (tokens[j].text == '(') - (tokens[j].text == ')')
            j += 1
        require(depth == 0, 'Unbalanced call: ' + name)
        yield tokens[i + 2:j - 1]


def contains(tokens, fragment):
    expected = kotlin_tokens(fragment)
    return any(tokens[i:i + len(expected)] == expected for i in range(len(tokens)))


def source(root, path):
    return kotlin_tokens((root / path).read_text())


def check_toolchain(root):
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
    for name, expected in {'agp': '9.4.1', 'kotlin': '2.2.10', 'ksp': '2.3.12',
                           'room': '2.8.5', 'mwaWalletlib': '2.0.7'}.items():
        require(catalog['versions'][name] == expected, 'Toolchain pin changed: ' + name)
    for name in ('solana-mobile-walletlib', 'solana-mobile-clientlib'):
        require(catalog['libraries'][name]['version']['ref'] == 'mwaWalletlib', 'MWA pin bypass: ' + name)
    props = dict(line.split('=', 1) for line in
                 (root / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines()
                 if line and not line.startswith('#'))
    require(props['distributionUrl'].replace('\\:', ':') ==
            'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip', 'Gradle distribution changed')
    require(props['distributionSha256Sum'] ==
            'bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01', 'Wrapper checksum changed')
    flags = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.lstrip().startswith('#'))
    for name in ('android.disallowKotlinSourceSets', 'android.builtInKotlin', 'android.newDsl'):
        require(flags.get(name) != 'false', 'Compatibility escape hatch: ' + name)
    modules = [t.text for args in calls(source(root, 'settings.gradle.kts'), 'include')
               for t in args if t.kind == 'string']
    require(sorted(modules) == [':app', ':demo-client'], 'Unexpected Gradle module')


def sql_layout(db):
    names = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name!='room_master_table'")]
    return {name: {'columns': list(db.execute('PRAGMA table_info(' + name + ')')),
                   'foreignKeys': list(db.execute('PRAGMA foreign_key_list(' + name + ')')),
                   'indexes': sorted((r[1], r[2], list(db.execute('PRAGMA index_info(' + r[1] + ')')))
                                     for r in db.execute('PRAGMA index_list(' + name + ')'))}
            for name in sorted(names)}


def exported_db(entities):
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for entity in entities.values():
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db


def check_schema(root):
    require(sha(root / (SCHEMAS + '1.json')) == V1_SHA, 'Historical schema v1 changed')
    v1, v2 = [json.loads((root / (SCHEMAS + str(v) + '.json')).read_text())['database'] for v in (1, 2)]
    require(v1['version'] == 1 and v2['version'] == 2, 'Exported schema version changed')
    old = {e['tableName']: e for e in v1['entities']}
    new = {e['tableName']: e for e in v2['entities']}
    columns = {
        'sessions': {'session_id', 'started_at_ms', 'completed_at_ms', 'dapp_identity_name', 'cluster', 'close_reason'},
        'protocol_events': {'event_id', 'session_id', 'sequence', 'method', 'started_at_ms', 'completed_at_ms',
                            'outcome', 'protocol_error_code', 'failure_source', 'injected_fault_id',
                            'request_summary_json', 'response_summary_json', 'capability_context_json'},
        'capability_snapshots': {'session_id', 'captured_at_ms', 'source', 'max_transactions', 'max_messages',
                                 'supported_versions_json', 'optional_features_json'},
        'transaction_diagnostics': {'transaction_id', 'session_id', 'event_id', 'payload_index',
                                    'fingerprint_sha256', 'wire_length', 'version', 'inspection_status', 'summary_json'},
    }
    require(set(new) == set(columns), 'Unexpected application tables')
    for table, names in columns.items():
        require({f['columnName'] for f in new[table]['fields']} == names, 'Unexpected/raw/secret column: ' + table)
    require(all(new[name] == entity for name, entity in old.items()), 'Phase 3 table definition changed')
    require(any(i['unique'] and i['columnNames'] == ['event_id', 'payload_index']
                for i in new['transaction_diagnostics']['indices']), 'Missing diagnostic payload uniqueness')
    db_source = source(root, BASE + 'storage/MwaLabDatabase.kt')
    annotation = list(calls(db_source, 'Database'))
    require(len(annotation) == 1 and contains(annotation[0], 'version = 2') and
            contains(annotation[0], 'exportSchema = true'), 'Room annotation must export schema 2')
    require(list(calls(db_source, 'Migration')) == [kotlin_tokens('1, 2')], 'Explicit migration 1 to 2 missing')
    require(list(calls(db_source, 'addMigrations')) == [kotlin_tokens('MIGRATION_1_2')], 'Migration registration missing')
    sql = []
    for args in calls(db_source, 'execSQL'):
        require(all(t.kind == 'string' or t.text in {'+', '.', 'trimIndent', '(', ')', ','} for t in args),
                'Migration SQL must be inspectable literal DDL')
        statement = ''.join(t.text for t in args if t.kind == 'string')
        require(re.match(r'\s*CREATE\s+(?:TABLE|(?:UNIQUE\s+)?INDEX)\s+IF\s+NOT\s+EXISTS\b', statement, re.I),
                'Migration must create empty diagnostic tables/indexes only; no history writes')
        sql.append(statement)
    require(len(sql) == 5, 'Expected two new tables and three indexes')
    # Execute actual migration DDL against v1, then compare SQLite constraints with v2.
    before, expected = exported_db(old), exported_db(new)
    try:
        for statement in sql:
            before.execute(statement)
        require(sql_layout(before) == sql_layout(expected), 'Migration SQL and exported v2 disagree')
        require(not list(before.execute('PRAGMA foreign_key_check')), 'Migration foreign keys invalid')
    finally:
        before.close()
        expected.close()


def runtime_paths(root):
    return sorted(str(p.relative_to(root)) for module in ('app', 'demo-client')
                  for p in (root / module / 'src/main').rglob('*.kt'))


def check_sources(root):
    require(sha(root / 'scripts/phase3_static.sh') == PHASE3_SHA, 'Frozen Phase 3 gate changed')
    require(sha(root / MANIFEST) == MANIFEST_SHA, 'Protected-source receipt changed without review')
    receipt = json.loads((root / MANIFEST).read_text())
    require(receipt['baselineCommit'] == BASELINE, 'Reviewed Phase 4 source baseline changed')
    require(runtime_paths(root) == sorted(receipt['runtimeSha256']), 'Unreviewed runtime file added/removed')
    for path, expected in receipt['predecessorSha256'].items():
        require(sha(root / path) == expected, 'Frozen predecessor source changed: ' + path)
    for path, expected in receipt['runtimeSha256'].items():
        require(sha(root / path) == expected, 'Reviewed Phase 4 integration changed: ' + path)
    historical = (root / 'docs/evidence/phase3/phase3-phase2-protected-sources.sha256').read_text()
    excluded = {BASE + 'approval/ApprovalCoordinator.kt', BASE + 'mwa/MobileWalletAdapterActivity.kt',
                'demo-client/src/main/java/dev/mwalab/democlient/DemoClientActivity.kt'}
    for line in historical.splitlines():
        expected, path = line.split()
        if path not in excluded:
            require(sha(root / path) == expected, 'Historical Phase 2 boundary changed: ' + path)
    require(set(receipt['reviewedAdditivePaths']) == excluded, 'Historical hash exceptions changed')


def check_diagnostic_authority(root):
    pure = set((root / (BASE + 'transaction')).glob('*.kt')) - {
        root / (BASE + 'transaction/' + name) for name in
        ('LegacyTransactionCodec.kt', 'SolanaTransactionMessageDetector.kt',
         'TransactionDiagnosticRepository.kt', 'TransactionDiagnosticSettlement.kt')}
    forbidden = {'LegacyTransactionCodec', 'LabSigningService', 'ApprovalCoordinator', 'IdentityRepository',
                 'MwaSessionHost', 'DevnetRpcGateway', 'requestApproval', 'signTransactions', 'signMessages',
                 'completeWithSignedPayloads', 'approve', 'SQLiteDatabase', 'RoomDatabase'}
    for path in pure:
        tokens = kotlin_tokens(path.read_text())
        require(not ({t.text for t in tokens if t.kind == 'id'} & forbidden),
                'Diagnostic signing/approval/storage dependency: ' + path.name)
        imports = re.findall(r'^import\s+([^\s]+)', path.read_text(), re.M)
        require(all(name.startswith(('java.math.', 'java.util.', 'java.nio.', 'dev.mwalab.transaction.',
                    'com.funkatronics.encoders.')) or name in
                    ('dev.mwalab.security.DiagnosticSanitizer', 'dev.mwalab.protocol.EventId', 'dev.mwalab.session.SessionId')
                    for name in imports), 'Unreviewed transitive diagnostic dependency: ' + path.name)
    provenance = source(root, BASE + 'capabilities/CapabilitySnapshotSource.kt')
    require([t.text for t in provenance if t.kind == 'id'] ==
            ['package', 'dev', 'mwalab', 'capabilities', 'enum', 'class', 'CapabilitySnapshotSource',
             'CONFIGURED_WALLETLIB_PROFILE'], 'Capability provenance changed')
    adapter = source(root, BASE + 'capabilities/CapabilitySnapshotFactory.kt')
    require(contains(adapter, 'MwaCapabilityProfile.snapshotForSession') and
            contains(adapter, 'source = CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE'),
            'Single-profile configured capability mapping missing')


def check_scope(root):
    capabilities_code = {BASE + 'protocol/ProtocolMethod.kt', BASE + 'protocol/recorder/PersistentProtocolRecorder.kt'}
    prohibited = {'simulateTransaction', 'SimulationEngine', 'SimulationService', 'FaultEngine', 'FaultProfile',
                  'ReportExporter', 'ReportExportService', 'ACTION_SEND', 'ACTION_SEND_MULTIPLE'}
    for path in runtime_paths(root):
        tokens = source(root, path)
        ids = {t.text for t in tokens if t.kind == 'id'}
        require(not ids.intersection(prohibited), 'Future-phase implementation: ' + path)
        require('GET_CAPABILITIES' not in ids or path in capabilities_code, 'Synthetic capabilities reference: ' + path)
        require(not any(t.text.startswith('fallbackToDestructiveMigration') for t in tokens if t.kind == 'id'),
                'Destructive migration fallback: ' + path)
        if path != BASE + 'security/NetworkPolicy.kt':
            require(not any(t.kind == 'string' and re.search(r'mainnet-beta|solana:mainnet', t.text) for t in tokens),
                    'Mainnet implementation: ' + path)
        require(not any(part.lower() in {'simulation', 'fault', 'faults', 'export', 'reports'}
                        for part in Path(path).parts), 'Future-phase package: ' + path)
    detail = source(root, BASE + 'ui/sessions/SessionDetailScreen.kt')
    require('MwaCapabilityProfile' not in {t.text for t in detail}, 'UI reads current capability authority')
    require(any(calls(detail, 'transactionInspectorContent')), 'Inspector is not attached to Session Detail')
    presentation = source(root, BASE + 'ui/transaction/TransactionPresentation.kt')
    titles = {t.text for t in presentation if t.kind == 'string'}
    require({'Overview', 'Accounts', 'Instructions', 'Raw metadata'} <= titles, 'Missing inspector sections')


def workflow_steps(text):
    """Read the existing workflow's scalar step mappings; reject duplicate names."""
    steps, current = {}, None
    for line in text.splitlines():
        match = re.fullmatch(r'      - name: (.+)', line)
        if match:
            current = match[1]
            require(current not in steps, 'Duplicate CI step')
            steps[current] = {}
        elif current:
            match = re.fullmatch(r'        (if|run): (.+)', line)
            if match:
                steps[current][match[1]] = match[2]
    return steps


def check_ci(root):
    text = (root / '.github/workflows/android.yml').read_text()
    require(re.search(r'^      - phase4-capability-transaction-diagnostics$', text, re.M), 'Phase 4 CI branch missing')
    steps = workflow_steps(text)
    v1 = "hashFiles('app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json')"
    v2 = "hashFiles('app/schemas/dev.mwalab.storage.MwaLabDatabase/2.json')"
    expected = {'Phase 2 historical scope gate': (f"{v1} == ''", './scripts/phase2_static.sh'),
                'Phase 3 persistent timeline and predecessor gate': (f"{v1} != '' && {v2} == ''", './scripts/phase3_static.sh'),
                'Phase 4 capability and transaction diagnostics gate': (f"{v2} != ''", './scripts/phase4_static.sh')}
    for name, (condition, command) in expected.items():
        require(steps.get(name) == {'if': '${{ ' + condition + ' }}', 'run': command}, 'Incorrect CI gate routing: ' + name)
    require('./scripts/phase4_static.sh' in steps['Make Gradle executable']['run'], 'Phase 4 executable permission missing')
    require('./scripts/phase4_static.sh' in steps['Validate shell gate syntax']['run'], 'Phase 4 shell syntax CI check missing')


def check_docs(root):
    for name in DOCS:
        require((root / name).is_file(), 'Missing Phase 4 document: ' + name)
        text = (root / name).read_text()
        require(not re.search(r'Phase 4 is (?:not started|NOT STARTED)', text), 'Obsolete current Phase 4 status: ' + name)
        for target in re.findall(r'\[[^\]\n]*\]\(([^)\s]+)\)', text):
            if '://' not in target and not target.startswith('#'):
                require((root / name).parent.joinpath(target.split('#')[0]).exists(), 'Broken document link: ' + name + ' -> ' + target)
    protocol_text = (root / 'docs/PROTOCOL_SUPPORT.md').read_text()
    marker = '## Phase 4 protocol and diagnostics truth table'
    require(marker in protocol_text, 'Missing Phase 4 protocol truth table')
    section = protocol_text.split(marker, 1)[1].split('\n## ', 1)[0]
    rows = {}
    for line in section.splitlines():
        if line.startswith('|'):
            fields = [f.strip() for f in line.strip('|').split('|')]
            if len(fields) >= 2 and fields[0] not in {'Capability', '---'}:
                require(fields[0] not in rows, 'Duplicate Phase 4 protocol support row')
                rows[fields[0]] = fields[1]
    require(all(rows.get(name) == status for name, status in SUPPORT.items()), 'Protocol support truth table mismatch')
    inventory = (root / 'PHASE_4_FILES.txt').read_text().splitlines()
    paths = [line for line in inventory if line and not line.startswith('#')]
    require(paths == sorted(set(paths)), 'Phase 4 file inventory must be sorted and unique')
    require(all(not Path(p).is_absolute() and '..' not in Path(p).parts and (root / p).is_file() for p in paths),
            'Invalid/missing Phase 4 inventory entry')
    require(set(DOCS) | {'PHASE_4_FILES.txt', 'scripts/phase4_static.sh', 'scripts/phase4_static.py'} <= set(paths),
            'Phase 4 inventory missing documentation/static files')
    for name in ('phase4-step4.11-device-acceptance.json', 'phase4-step4.11-local-gates.json'):
        evidence = json.loads((root / 'docs/evidence/phase4' / name).read_text())
        require(evidence['status'] == 'PASS' and evidence['phase'] == '4.11', 'Existing acceptance evidence missing')
    preflight = json.loads((root / 'docs/evidence/phase4/phase4-step4.14-preflight.json').read_text())
    require(preflight['phase3StaticSha256'] == PHASE3_SHA, 'Preflight frozen hash receipt mismatch')


CHECKS = (check_toolchain, check_schema, check_sources, check_diagnostic_authority, check_scope, check_ci, check_docs)


def validate(root):
    for check in CHECKS:
        check(root)
        print(check.__name__ + ': PASS')


if __name__ == '__main__':
    try:
        validate(Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve())
    except (GateError, OSError, ValueError, KeyError, sqlite3.Error) as error:
        raise SystemExit('PHASE 4 STATIC GATE ERROR: ' + str(error))
