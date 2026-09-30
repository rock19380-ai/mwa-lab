#!/usr/bin/env python3
"""Real cross-package Phase 5 device acceptance using the debug APKs.

Requires an unlocked adb device/emulator, installed debug APKs, and enough Devnet
lamports on the persistent MWA Lab identity to pay one transaction fee. It drives
only visible UI text and stores screenshots/UI XML plus a preliminary JSON summary.
It cannot claim database binding or restart persistence by itself.
"""
from __future__ import annotations
import argparse, json, re, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'docs/evidence/phase5'
DEMO='dev.mwalab.democlient/.DemoClientActivity'
EXTRA='mwa_phase5_scenario'
SCENARIOS={
    'GOOD_PASS_APPROVE': ('Simulation PASS','APPROVE',True),
    'BAD_FAIL_APPROVE': ('Simulation FAIL','APPROVE',True),
    'GOOD_PASS_REJECT': ('Simulation PASS','REJECT',False),
}

def adb(*args, capture=True):
    cmd=['adb',*args]
    if capture:
        return subprocess.check_output(cmd, text=True, stderr=subprocess.STDOUT)
    subprocess.run(cmd, check=True)
    return ''

def dump_xml():
    remote='/sdcard/mwa_phase5_ui.xml'
    adb('shell','uiautomator','dump',remote)
    return adb('exec-out','cat',remote)

def nodes(xml_text):
    root=ET.fromstring(xml_text)
    return list(root.iter('node'))

def find_text(xml_text, needle, substring=False):
    for n in nodes(xml_text):
        value=n.attrib.get('text','')
        if (needle in value) if substring else (value == needle):
            return n
    return None

def center(bounds):
    m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',bounds or '')
    if not m: raise RuntimeError('node has no parseable bounds')
    x1,y1,x2,y2=map(int,m.groups())
    return (x1+x2)//2,(y1+y2)//2

def tap_node(node):
    x,y=center(node.attrib.get('bounds'))
    adb('shell','input','tap',str(x),str(y))

def wait_for_text(needle, timeout=60, substring=False, scroll=False):
    deadline=time.time()+timeout
    last=''
    swipes=0
    while time.time()<deadline:
        try:
            last=dump_xml()
            failure=next((n.attrib.get('text','') for n in nodes(last)
                if re.fullmatch(r'PHASE5 [A-Z_]+: FAIL',n.attrib.get('text',''))),None)
            if failure is not None:
                detail=next((n.attrib.get('text','') for n in nodes(last)
                    if n.attrib.get('text','').startswith('Failure: ')),'Failure detail unavailable')
                raise RuntimeError(f'{failure}; {detail}')
            n=find_text(last,needle,substring)
            if n is not None: return n,last
        except RuntimeError:
            raise
        except Exception:
            pass
        if scroll and swipes < 8:
            adb('shell','input','swipe','500','1500','500','650','300')
            swipes += 1
        time.sleep(1)
    raise RuntimeError(f'timeout waiting for UI text: {needle}')

def capture(label):
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    xml=dump_xml()
    (EVIDENCE/f'{label}.xml').write_text(xml)
    with (EVIDENCE/f'{label}.png').open('wb') as out:
        subprocess.run(['adb','exec-out','screencap','-p'],check=True,stdout=out)
    return xml

def run_scenario(name):
    expected_sim, decision, should_sign=SCENARIOS[name]
    adb('shell','am','force-stop','dev.mwalab.democlient')
    adb('shell','am','start','-n',DEMO,'--es',EXTRA,name)
    simulate,_=wait_for_text('SIMULATE',timeout=90,scroll=True)
    tap_node(simulate)
    wait_for_text(expected_sim,timeout=45,scroll=True)
    if expected_sim == 'Simulation PASS':
        wait_for_text('Simulation passed on Devnet at the recorded context. This does not guarantee later signing, submission, confirmation, or unchanged chain state.',timeout=10,scroll=True)
    sim_xml=capture(f'phase5-device-{name.lower()}-simulation')
    if name == 'BAD_FAIL_APPROVE':
        if 'Simulation failure source: SIMULATION' not in sim_xml:
            raise RuntimeError('runtime FAIL did not show SIMULATION failure source')
    decision_node,_=wait_for_text(decision,timeout=10)
    tap_node(decision_node)
    _,final_xml=wait_for_text(f'PHASE5 {name}: PARENT RESPONSE VERIFIED',timeout=120,substring=False,scroll=True)
    final_xml=capture(f'phase5-device-{name.lower()}-parent')
    if 'Submitted transactions: 0' not in final_xml:
        raise RuntimeError('sign_transactions acceptance did not prove zero submissions')
    if should_sign and 'Signature verified: true' not in final_xml:
        raise RuntimeError('approved parent response did not verify signature')
    if not should_sign and 'Expected protocol error:' not in final_xml:
        raise RuntimeError('rejection did not surface authoritative protocol error')
    fingerprint_node=find_text(final_xml,'MWA Lab payload fingerprint: ',substring=True)
    if fingerprint_node is None:
        raise RuntimeError('Demo Client did not show a safe transaction fingerprint')
    fingerprint=fingerprint_node.attrib.get('text','').split(': ',1)[-1]
    if re.fullmatch(r'[0-9a-f]{64}',fingerprint) is None:
        raise RuntimeError('Demo Client fingerprint is malformed')
    return {'scenario':name,'simulationUi':expected_sim.replace('Simulation ',''),'decision':decision,
            'fingerprintSha256':fingerprint,'submittedTransactionsReportedByDemo':0,
            'parentWireResponse':'VERIFIED_BY_DEMO','diagnosticDatabaseVerified':False}

def preliminary_summary(results):
    # UI and demo wire checks alone cannot prove canonical parent/child Room binding
    # or restart persistence. Keep this receipt explicitly preliminary.
    return {'phase':'5-device-acceptance','status':'UI_ONLY_PENDING_DATABASE_AND_RESTART_VERIFICATION',
            'network':'solana:devnet','crossPackagePath':True,'scenarios':results,
            'databaseByteScan':'NOT_RUN','restartPersistence':'NOT_RUN','freezeTagCreated':False}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--scenario',choices=[*SCENARIOS,'ALL'],default='ALL')
    args=ap.parse_args()
    adb('get-state')
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    selected=list(SCENARIOS) if args.scenario=='ALL' else [args.scenario]
    results=[]
    for name in selected:
        print(f'=== {name} ===',flush=True)
        results.append(run_scenario(name))
        print(f'{name}: UI AND DEMO WIRE CHECKS PASS; DATABASE/RESTART PENDING',flush=True)
    summary=preliminary_summary(results)
    path=EVIDENCE/'phase5-device-acceptance.json'
    path.write_text(json.dumps(summary,indent=2)+'\n')
    print(path.read_text(),end='')

if __name__=='__main__':
    try: main()
    except subprocess.CalledProcessError as exc:
        print(exc.output or str(exc),file=sys.stderr); sys.exit(exc.returncode or 1)
    except RuntimeError as exc:
        print(str(exc),file=sys.stderr); sys.exit(1)
