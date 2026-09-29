#!/usr/bin/env python3
"""Real Phase 4 cross-package/sign-only/restart acceptance. No fabricated evidence.

Requires both debug APKs installed. Reads only mwa_lab.db, never walletlib token
storage or key material. Database copies remain in /tmp; only sanitized diagnostic
rows, product screenshots, APK hashes, and assertions enter the evidence directory.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

TABLES = ("sessions", "protocol_events", "capability_snapshots", "transaction_diagnostics")
ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    options = parser.parse_args()
    options.evidence_dir.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix="mwa-phase4-diagnostics-"))
    checks, results = [], []
    expected = {}
    for line in (ROOT/"test-vectors/transactions/expected.properties").read_text().splitlines():
        if line and not line.startswith("#"):
            k,v = line.split("=",1)
            expected[k] = v

    def adb(*args, required=True):
        response = subprocess.run(["adb","-s",options.serial,*args], capture_output=True, timeout=45)
        if required and response.returncode:
            raise AssertionError("ADB operation failed: "+" ".join(args[:4]))
        return response

    assert adb("get-state").stdout.strip()==b"device", "Device is unavailable"
    for package in ("dev.mwalab", "dev.mwalab.democlient"):
        assert adb("shell","pm","path",package).stdout.startswith(b"package:"), "Install both debug APKs first"

    def ui():
        end = time.monotonic()+25
        while time.monotonic()<end:
            dump = adb("shell","uiautomator","dump","/sdcard/mwa-phase4-acceptance.xml",required=False)
            if dump.returncode==0 and b"dumped to" in dump.stdout:
                text = adb("exec-out","cat","/sdcard/mwa-phase4-acceptance.xml",required=False)
                try:
                    tree = ET.fromstring(text.stdout)
                    if tree.tag=="hierarchy":
                        return tree
                except ET.ParseError:
                    pass
            time.sleep(.25)
        raise AssertionError("Could not capture a valid live UI hierarchy")

    def node(tree, text=None, resource=None, prefix=None):
        return next((n for n in tree.iter("node") if
            (text is None or n.get("text")==text) and
            (prefix is None or n.get("text","").startswith(prefix)) and
            (resource is None or n.get("resource-id")==resource)),None)

    def wait(predicate, label, seconds=60):
        end = time.monotonic()+seconds
        while time.monotonic()<end:
            tree = ui()
            failure = next((n.get("text") for n in tree.iter("node") if
                re.fullmatch(r"PHASE4 \w+: FAIL", n.get("text",""))),None)
            assert failure is None, "Demo Client reported "+str(failure)+"; no acceptance success recorded"
            value = predicate(tree)
            if value is not None:
                return value
            time.sleep(.25)
        raise AssertionError("Timed out: "+label)

    def bounds(n):
        return list(map(int,re.findall(r"\d+",n.get("bounds",""))))

    def tap(n):
        x1,y1,x2,y2 = bounds(n)
        adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))

    def click(text):
        tap(wait(lambda tree:node(tree,text=text),text))

    def scroll(tree):
        container = next((n for n in tree.iter("node") if n.get("scrollable")=="true"),None)
        assert container is not None, "No scrollable diagnostic UI"
        x1,y1,x2,y2 = bounds(container)
        x = (x1+x2)//2
        adb("shell","input","swipe",str(x),str(y1+(y2-y1)*3//4),
            str(x),str(y1+(y2-y1)//3),"500")

    def seek(predicate, label):
        for _ in range(55):
            tree = ui()
            found = predicate(tree)
            if found is not None:
                return found
            scroll(tree)
        raise AssertionError("Diagnostic UI field absent: "+label)

    def photo(label):
        name = "phase4-step4.11-"+label+".png"
        png = adb("exec-out","screencap","-p").stdout
        assert png.startswith(b"\x89PNG")
        (options.evidence_dir/name).write_bytes(png)
        return name

    def snapshot(label):
        directory = scratch/label
        directory.mkdir()
        for suffix in ("","-wal","-shm"):
            present = adb("shell","run-as","dev.mwalab","test","-f","databases/mwa_lab.db"+suffix,required=False)
            if suffix and present.returncode:
                continue
            assert present.returncode==0, "Diagnostic DB missing"
            raw = adb("exec-out","run-as","dev.mwalab","cat","databases/mwa_lab.db"+suffix).stdout
            if not suffix:
                assert raw.startswith(b"SQLite format 3\x00")
            (directory/("mwa_lab.db"+suffix)).write_bytes(raw)
        db = sqlite3.connect(directory/"mwa_lab.db")
        db.row_factory = sqlite3.Row
        try:
            assert db.execute("PRAGMA user_version").fetchone()[0]==2
            assert not list(db.execute("PRAGMA foreign_key_check")), "Orphan diagnostic relationships"
            data = {table:sorted([dict(r) for r in db.execute("SELECT * FROM "+table)],
                key=lambda r:json.dumps(r,sort_keys=True)) for table in TABLES}
        finally:
            db.close()
        # Persisted diagnostic schema and summaries must not gain raw/secret fields.
        for row in data["transaction_diagnostics"]:
            summary = json.loads(row["summary_json"])
            def check_fields(value):
                if isinstance(value,dict):
                    for k,v in value.items():
                        assert not any(bad in k.lower() for bad in
                            ("auth_token","private_key","seed","encryption","raw_transaction","data_hex","memo_text"))
                        check_fields(v)
                elif isinstance(value,list):
                    for v in value: check_fields(v)
            check_fields(summary)
        return data

    adb("shell","am","start","-n","dev.mwalab/.MainActivity","-f","0x14000000")
    wait(lambda tree:node(tree,text="Persistent protocol debugger"),"initial Home")
    adb("shell","am","force-stop","dev.mwalab")
    baseline = snapshot("baseline")
    original_ids = {r["session_id"] for r in baseline["sessions"]}

    for scenario in ("SYSTEM_TRANSFER","UNKNOWN_PROGRAM","V0_REJECT"):
        adb("shell","am","start","-S","-n","dev.mwalab.democlient/.DemoClientActivity",
            "--es","mwa_phase4_scenario",scenario)
        preapproval = None
        if scenario!="V0_REJECT":
            wait(lambda tree:node(tree,text="APPROVE"),scenario+" explicit approval")
            initial = ui()
            for warning in ("MWA LAB TEST ENDPOINT","SOLANA DEVNET","NO REAL FUNDS"):
                assert node(initial,text=warning) is not None
            payer = seek(lambda tree:node(tree,prefix="Fee payer: "),"verified fee payer").get("text")[11:]
            fingerprint = seek(lambda tree:node(tree,prefix="MWA Lab payload fingerprint: "),"original fingerprint").get("text").split(": ",1)[1]
            assert re.fullmatch("[0-9a-f]{64}",fingerprint)
            program = "System Program" if scenario=="SYSTEM_TRANSFER" else "Unknown Program"
            seek(lambda tree:node(tree,text="Instruction 1: "+program),program)
            if scenario=="SYSTEM_TRANSFER":
                seek(lambda tree:node(tree,text="From: "+payer),"exact transfer sender")
                seek(lambda tree:node(tree,text="To: "+expected["legacy-system-transfer.decoded_to"]),"exact transfer destination")
                seek(lambda tree:node(tree,text="Lamports: 10000000"),"exact lamports")
                seek(lambda tree:node(tree,text="DEVNET SOL: 0.01"),"exact SOL")
            else:
                seek(lambda tree:node(tree,text="Program ID: "+expected["legacy-unknown-program.program_id"]),"unknown program ID")
                seek(lambda tree:node(tree,text="Unknown semantics"),"explicit unknown semantics")
                seek(lambda tree:node(tree,text="Instruction data SHA-256: "+expected["legacy-unknown-program.data_sha256"]),"unknown data hash")
            preapproval = photo(scenario.lower()+"-before-approval")
            click("APPROVE")
            wait(lambda tree:node(tree,text="PHASE4 "+scenario+": PASS"),scenario+" signed response")
        else:
            def v0_result(tree):
                passed = node(tree,text="PHASE4 V0_REJECT: PASS")
                return passed if passed is not None else node(tree,text="APPROVE")
            outcome = wait(v0_result, "v0 authoritative rejection")
            assert outcome.get("text")!="APPROVE", "v0 unexpectedly reached signing approval"
            tree = ui()
            fingerprint = node(tree,prefix="MWA Lab payload fingerprint: ").get("text").split(": ",1)[1]
            payer = node(tree,prefix="Fee payer: ").get("text")[11:]
            rejection = node(tree,prefix="Expected protocol error: ")
            assert rejection is not None
            expected_error = int(rejection.get("text").split(": ",1)[1])
            photo("v0-authoritative-rejection")
        end = time.monotonic()+25
        for attempt in range(30):
            data = snapshot(scenario+"-"+str(attempt))
            rows = [r for r in data["transaction_diagnostics"] if r["fingerprint_sha256"]==fingerprint and r["session_id"] not in original_ids]
            if len(rows)==1:
                row = rows[0]
                session = next(r for r in data["sessions"] if r["session_id"]==row["session_id"])
                if session["completed_at_ms"] is not None:
                    break
            assert time.monotonic()<end, "Terminal diagnostics did not settle"
            time.sleep(.3)
        else:
            raise AssertionError("Terminal diagnostics did not settle")
        timeline = sorted([r for r in data["protocol_events"] if r["session_id"]==session["session_id"]],key=lambda r:r["sequence"])
        assert [r["sequence"] for r in timeline]==[1,2,3]
        assert [r["method"] for r in timeline]==["AUTHORIZE","SIGN_TRANSACTIONS","DEAUTHORIZE"]
        assert session["cluster"]=="solana:devnet" and session["close_reason"]=="SERVING_COMPLETE"
        assert session["dapp_identity_name"]=="MWA Lab Demo Client — FOR TESTING ONLY"
        event = timeline[1]
        assert row["session_id"]==session["session_id"] and row["event_id"]==event["event_id"] and row["payload_index"]==0
        request = json.loads(event["request_summary_json"])
        assert request["payload_0_sha256"]==fingerprint and request["payload_0_length"]==str(row["wire_length"])
        capability = next(r for r in data["capability_snapshots"] if r["session_id"]==session["session_id"])
        assert capability["source"]=="CONFIGURED_WALLETLIB_PROFILE"
        assert capability["captured_at_ms"]>=session["started_at_ms"]
        assert json.loads(capability["supported_versions_json"])==["legacy"]
        assert capability["max_transactions"]==10 and capability["max_messages"]==10
        summary = json.loads(row["summary_json"])
        assert summary["session_id"]==row["session_id"] and summary["event_id"]==row["event_id"]
        assert summary["fingerprint_sha256"]==fingerprint and summary["fee_payer"]==payer
        decoded = summary["instructions"][0]["decoded"]
        fixture = {"SYSTEM_TRANSFER":"legacy-system-transfer","UNKNOWN_PROGRAM":"legacy-unknown-program","V0_REJECT":"v0-unresolved-lookup"}[scenario]
        assert row["wire_length"]==int(expected[fixture+".wire_length"])
        assert row["version"]==expected[fixture+".version"] and row["inspection_status"]==expected[fixture+".status"]
        assert decoded["kind"]==expected[fixture+".decoded"]
        if scenario=="V0_REJECT":
            assert event["outcome"]=="FAILURE" and event["protocol_error_code"]==expected_error
            assert event["failure_source"]=="LOCAL_PARSER" and decoded["reason"]=="UNRESOLVED_ACCOUNTS"
        else:
            assert event["outcome"]=="SUCCESS" and event["protocol_error_code"] is None and event["failure_source"]=="NONE"
            if scenario=="SYSTEM_TRANSFER":
                assert decoded=={"kind":"SYSTEM_TRANSFER","from":payer,"to":expected[fixture+".decoded_to"],"lamports":"10000000"}
        results.append({"scenario":scenario,"session":session,"event":event,"capability":capability,
            "transaction":summary,"preapprovalScreenshot":preapproval})
        checks.append({"gate":scenario+"-real-cross-app-terminal-binding","result":"PASS"})
        print(scenario+" cross-app, diagnostics and terminal binding PASS",flush=True)

    adb("shell","am","force-stop","dev.mwalab")
    stopped = snapshot("force-stopped")
    assert len({r["session_id"] for r in stopped["sessions"]}-original_ids)==3
    for table in TABLES:
        historical = [row for row in stopped[table] if row["session_id"] in original_ids]
        assert historical == baseline[table], "Preexisting history changed or was backfilled"
    adb("shell","am","start","-n","dev.mwalab/.MainActivity","-f","0x14000000")
    wait(lambda tree:node(tree,text="Persistent protocol debugger"),"restart Home")
    assert snapshot("reopened")==stopped, "Persistent structured data changed after process restart"
    checks.append({"gate":"force-stop-restart-all-four-tables-equal","result":"PASS"})

    for result in results:
        click("Sessions")
        target = "session-"+result["session"]["session_id"]
        card = seek(lambda tree:node(tree,resource=target),"restart session "+target)
        tap(card)
        status = "FAIL" if result["scenario"]=="V0_REJECT" else "PASS"
        wait(lambda tree:node(tree,text="SESSION "+status),"restart detail")
        seek(lambda tree:node(tree,text="CAPABILITY SNAPSHOT"),"recorded capability")
        seek(lambda tree:node(tree,text="Source: Configured MWA Lab walletlib profile"),"configured provenance")
        seek(lambda tree:node(tree,text="#2 SIGN_TRANSACTIONS"),"real terminal event")
        seek(lambda tree:node(tree,text="Inspect transaction 1"),"stored transaction")
        click("Inspect transaction 1")
        seek(lambda tree:node(tree,text="Overview"),"inspector overview")
        if result["scenario"]=="V0_REJECT":
            seek(lambda tree:node(tree,text="Inspection status: Partial inspection"),"v0 partial")
            seek(lambda tree:node(tree,text="Version: v0"),"v0 version")
        elif result["scenario"]=="UNKNOWN_PROGRAM":
            seek(lambda tree:node(tree,text="Unknown Program"),"stored explicit unknown")
            seek(lambda tree:node(tree,text="Unknown semantics"),"stored unknown semantics")
        else:
            seek(lambda tree:node(tree,text="System Program"),"stored System Program")
            seek(lambda tree:node(tree,text="Lamports: 10000000"),"stored exact transfer")
        result["restartScreenshot"] = photo(result["scenario"].lower()+"-restart-inspector")
        checks.append({"gate":result["scenario"]+"-restart-product-inspector","result":"PASS"})

    evidence = {"status":"PASS","phase":"4.11","serial":options.serial,
        "capturedAtUtc":datetime.datetime.now(datetime.timezone.utc).isoformat(),"schemaVersion":2,
        "getCapabilitiesProtocolEventCreated":False,"submittedTransactions":0,
        "device":{"sdk":adb("shell","getprop","ro.build.version.sdk").stdout.decode().strip(),
            "model":adb("shell","getprop","ro.product.model").stdout.decode().strip()},
        "apkSha256":{module:hashlib.sha256((ROOT/module/"build/outputs/apk/debug"/filename).read_bytes()).hexdigest()
            for module,filename in (("app","app-debug.apk"),("demo-client","demo-client-debug.apk"))},
        "checks":checks,"scenarios":results,
        "restartTableSha256":{table:hashlib.sha256(json.dumps(stopped[table],sort_keys=True).encode()).hexdigest() for table in TABLES}}
    (options.evidence_dir/"phase4-step4.11-device-acceptance.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
    print("PHASE 4.11 REAL CROSS-APP / PERSISTENT INSPECTOR ACCEPTANCE: PASS",flush=True)
    print("Private diagnostic DB copies (not committed): "+str(scratch),flush=True)


if __name__=="__main__":
    main()
