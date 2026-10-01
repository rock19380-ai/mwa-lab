#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import hashlib, re, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
BASE='bcd42c11adbe18abdbea18da3f29ae302c9518be'

def req(cond,msg):
    if not cond: raise AssertionError(msg)
def t(rel):
    p=ROOT/rel; req(p.is_file(),f'missing {rel}'); return p.read_text(errors='ignore')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def ancestry():
    subprocess.run(['git','-c',f'safe.directory={ROOT}','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)

def baseline_manifest():
    p=ROOT/'docs/evidence/phase6/phase6-baseline.sha256'; req(p.is_file(),'baseline manifest missing')
    for line in p.read_text().splitlines():
        line=line.strip()
        if not line or line.startswith('#'): continue
        expected, rel=line.split(None,1); target=ROOT/rel
        req(target.is_file(),f'baseline target missing: {rel}')
        req(sha(target)==expected,f'frozen Phase 5 baseline changed: {rel}')

def schema():
    db=t('app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt')
    req(re.search(r'\bversion\s*=\s*3\b',db) is not None,'Room version is not 3')
    req('MIGRATION_1_2' in db and 'MIGRATION_2_3' in db,'historical migrations missing')
    for n in (1,2,3): req((ROOT/f'app/schemas/dev.mwalab.storage.MwaLabDatabase/{n}.json').is_file(),f'schema {n} missing')
    req(not (ROOT/'app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json').exists(),'schema 4 must not exist')

def deps():
    libs=t('gradle/libs.versions.toml')
    req(re.search(r'(?m)^mwaWalletlib\s*=\s*"2\.0\.7"\s*$',libs) is not None,'walletlib drift')

def fault_contract():
    ids=t('app/src/main/java/dev/mwalab/faults/FaultId.kt')
    for sid in ['NORMAL','FAULT_AUTH_REJECT','FAULT_SIGN_REJECT','FAULT_DELAY_5S','FAULT_UNSUPPORTED_CHAIN','FAULT_INVALID_PAYLOAD','FAULT_TOO_MANY_PAYLOADS','FAULT_RPC_UNAVAILABLE','FAULT_SUBMISSION_FAILURE','FAULT_STALE_BLOCKHASH']:
        req(sid in ids,f'fault id missing: {sid}')
    host=t('app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt')
    for hook in ['AUTHORIZATION_DECISION','SIGNING_VALIDATION','SIGNING_PRE_APPROVAL','TRANSACTION_BLOCKHASH_CHECK','SUBMISSION_PRE_RPC']:
        req(hook in host,f'host hook missing: {hook}')
    req('markInjectedFault' in host,'canonical injected-fault annotation missing')
    rec=t('app/src/main/java/dev/mwalab/protocol/recorder/PersistentProtocolRecorder.kt')
    req('markInjectedFault' in rec,'recorder annotation missing')
    req('FaultEvidenceInvariant' in rec,'recorder fault invariant missing')

def ui_and_vectors():
    fault_ui=t('app/src/main/java/dev/mwalab/ui/faults/FaultLabScreen.kt')
    req('FAULT ACTIVE' in fault_ui and 'RETURN TO NORMAL' in fault_ui,'Fault Lab truthfulness controls missing')
    approval=t('app/src/main/java/dev/mwalab/approval/SigningApprovalScreen.kt')
    req('request-fault-banner' in approval and 'faultSnapshotId' in approval,'request snapshot warning missing')
    presentation=t('app/src/main/java/dev/mwalab/ui/sessions/SessionPresentation.kt')
    req('injectedFaultId' in presentation and 'failureSource' not in presentation.split('injectedConditionText',1)[1].split('}',1)[0],
        'injected condition presentation appears inferred from failure source')
    req((ROOT/'test-vectors/faults/faults.properties').is_file(),'fault vectors missing')
    req((ROOT/'demo-client/src/main/java/dev/mwalab/democlient/Phase6AcceptanceRunner.kt').is_file(),'Phase 6 acceptance runner missing')

def phase5_separation():
    sim='\n'.join(p.read_text(errors='ignore') for p in (ROOT/'app/src/main/java/dev/mwalab/simulation').glob('*.kt'))
    for forbidden in ('LabSigningService','ApprovalCoordinator','sendTransaction(','awaitCommitment('):
        req(forbidden not in sim,f'Phase 5 simulation authority leak: {forbidden}')

def ci():
    ci=t('.github/workflows/android.yml')
    req('phase6-deterministic-fault-engine' in ci,'Phase 6 branch missing from CI')
    req('phase6_static.sh' in ci,'Phase 6 gate missing from CI')
    req("hashFiles('scripts/phase6_static.sh')" in ci,'Phase 6 marker routing missing')
    req('phase5_static.sh' in ci,'historical Phase 5 gate disappeared')

def scope():
    allmain='\n'.join(p.read_text(errors='ignore') for p in (ROOT/'app/src/main').rglob('*.kt'))
    req('ACTION_SEND' not in allmain and 'ACTION_SEND_MULTIPLE' not in allmain,'Phase 7 sharing introduced early')
    for name in ('ReportExporter.kt','ReportExportService.kt'):
        req(not any(p.name==name for p in (ROOT/'app/src/main').rglob('*.kt')),f'Phase 7 exporter introduced: {name}')

def docs():
    for rel in ['README.md','docs/ARCHITECTURE.md','docs/SECURITY.md','docs/TESTING.md','docs/PROTOCOL_SUPPORT.md','docs/DEMO.md','CHANGELOG.md','PHASE_6_REPORT.md','PHASE_6_FILES.txt','docs/evidence/phase6/README.md']:
        req((ROOT/rel).is_file(),f'Phase 6 doc missing: {rel}')
    req('Phase 7' in t('PHASE_6_REPORT.md'),'report must preserve Phase 7 boundary')

def main():
    for f in (ancestry,baseline_manifest,schema,deps,fault_contract,ui_and_vectors,phase5_separation,ci,scope,docs): f()
    print('PHASE 6 STATIC GATE: PASS')
if __name__=='__main__':
    try: main()
    except Exception as e:
        print(f'PHASE 6 STATIC GATE: FAIL: {e}',file=sys.stderr); raise
