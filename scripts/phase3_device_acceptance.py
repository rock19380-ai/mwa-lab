#!/usr/bin/env python3
"""Execute existing cross-package approval flows and prove persistent UI restart.

Requires the debug app + demo-client installed on the selected device. Uses no
wallet secrets, signer shortcuts, or fabricated protocol events. Output is safe
structured acceptance evidence; SQLite copies and screenshots stay in /tmp.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--evidence", required=True)
    options = parser.parse_args()
    scratch = Path(tempfile.mkdtemp(prefix="mwa-phase3-device-"))
    checks = []

    def adb(*args, required=True):
        result = subprocess.run(["adb", "-s", options.serial, *args], capture_output=True)
        if required and result.returncode:
            raise RuntimeError("ADB command failed: " + " ".join(args[:4]))
        return result

    def ui():
        adb("shell", "uiautomator", "dump", "/sdcard/mwa-phase3-acceptance.xml")
        return ET.fromstring(adb("exec-out", "cat", "/sdcard/mwa-phase3-acceptance.xml").stdout)

    def node(tree, text=None, resource=None):
        return next((n for n in tree.iter("node") if
                     (text is None or n.get("text") == text) and
                     (resource is None or n.get("resource-id") == resource)), None)

    def wait_for(predicate, label, timeout=45):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            tree = ui()
            value = predicate(tree)
            if value is not None:
                return value
            time.sleep(0.25)
        raise AssertionError("Timed out: " + label)

    def tap(n):
        bounds = [int(v) for v in re.findall(r"[0-9]+", n.get("bounds"))]
        adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))

    def click(text):
        tap(wait_for(lambda tree: node(tree, text=text), text))

    def snapshot(label):
        directory = scratch / label
        directory.mkdir()
        for suffix in ("", "-wal", "-shm"):
            exists = adb("shell", "run-as", "dev.mwalab", "test", "-f", "databases/mwa_lab.db"+suffix, required=False)
            if exists.returncode != 0 and suffix:
                continue
            assert exists.returncode == 0, "Diagnostic database has not been created"
            result = adb("exec-out", "run-as", "dev.mwalab", "cat", "databases/mwa_lab.db"+suffix)
            if not suffix:
                assert result.stdout.startswith(b"SQLite format 3\x00"), "Invalid SQLite snapshot header"
            (directory/("mwa_lab.db"+suffix)).write_bytes(result.stdout)
        connection = sqlite3.connect(directory/"mwa_lab.db")
        connection.row_factory = sqlite3.Row
        try:
            sessions = [dict(row) for row in connection.execute("SELECT * FROM sessions ORDER BY started_at_ms DESC")]
            events = [dict(row) for row in connection.execute("SELECT * FROM protocol_events ORDER BY session_id, sequence")]
        finally:
            connection.close()
        raw = b"".join(file.read_bytes() for file in directory.iterdir() if file.is_file())
        for forbidden in (b"MWA Lab Phase 2 cross-app approval acceptance", b"https://phase2-demo-client.invalid"):
            assert forbidden not in raw, "Raw client material persisted in SQLite"
        return sessions, events

    # Connected tests may uninstall their target app at teardown. Initialize
    # the installed product database through Home before the first snapshot.
    adb("shell", "am", "start", "-n", "dev.mwalab/.MainActivity", "-f", "0x14000000")
    def history_ready(tree):
        for label in ("No protocol sessions yet", "Last session"):
            found = node(tree, text=label)
            if found is not None:
                return found
        return None
    wait_for(history_ready, "initial persistent Home state")
    before, _ = snapshot("before")
    original_ids = {s["session_id"] for s in before}
    for scenario, decision in (("SIGN_MESSAGE_APPROVE", "APPROVE"), ("SIGN_MESSAGE_REJECT", "REJECT")):
        adb("shell", "am", "start", "-S", "-n", "dev.mwalab.democlient/.DemoClientActivity",
            "--es", "mwa_phase2_scenario", scenario)
        click(decision)
        wait_for(lambda tree: node(tree, text="PHASE2 "+scenario+": PASS"), scenario+" client result")
        checks.append({"gate": "cross-package-"+scenario, "result": "PASS"})
        print(scenario+" cross-package response PASS", flush=True)

    # Stable byte snapshot after stopping the wallet process, before reopening.
    adb("shell", "am", "force-stop", "dev.mwalab")
    sessions, events = snapshot("stopped")
    new_sessions = [s for s in sessions if s["session_id"] not in original_ids]
    assert len(new_sessions) == 2, "Expected exactly two newly observed sessions"
    selected = {}
    for session in new_sessions:
        timeline = [e for e in events if e["session_id"] == session["session_id"]]
        assert [e["sequence"] for e in timeline] == [1, 2, 3]
        assert [e["method"] for e in timeline] == ["AUTHORIZE", "SIGN_MESSAGES", "DEAUTHORIZE"]
        assert session["cluster"] == "solana:devnet"
        assert session["dapp_identity_name"] == "MWA Lab Demo Client — FOR TESTING ONLY"
        assert session["close_reason"] == "SERVING_COMPLETE"
        assert session["completed_at_ms"] is not None
        status = "FAIL" if timeline[1]["outcome"] == "FAILURE" else "PASS"
        assert timeline[1]["outcome"] in ("FAILURE", "SUCCESS")
        if status == "FAIL":
            assert timeline[1]["protocol_error_code"] == -3
            assert timeline[1]["failure_source"] == "OBSERVED_PROTOCOL"
        else:
            assert timeline[1]["protocol_error_code"] is None
            assert timeline[1]["failure_source"] == "NONE"
        assert all(e["injected_fault_id"] is None and e["capability_context_json"] is None for e in timeline)
        selected[status] = (session, timeline)
    assert set(selected) == {"PASS", "FAIL"}

    adb("shell", "am", "start", "-n", "dev.mwalab/.MainActivity", "-f", "0x14000000")
    wait_for(lambda tree: node(tree, text="Persistent protocol debugger"), "restarted Home")
    reloaded_sessions, reloaded_events = snapshot("reopened")
    assert {s["session_id"]: s for s in reloaded_sessions} == {s["session_id"]: s for s in sessions}
    assert reloaded_events == events, "Timeline changed across process restart"
    checks.append({"gate": "force-stop-relaunch-sqlite-equality", "result": "PASS"})

    for status in ("FAIL", "PASS"):
        click("Sessions")
        session, timeline = selected[status]
        target = "session-"+session["session_id"]
        for _ in range(12):
            match = node(ui(), resource=target)
            if match is not None:
                tap(match)
                break
            adb("shell", "input", "swipe", "540", "1800", "540", "600", "250")
        else:
            raise AssertionError("Restart-loaded session absent from Sessions UI")
        wait_for(lambda tree: node(tree, text="SESSION "+status), "detail "+status)
        # Search forward in canonical sequence for the signing event/error.
        for _ in range(12):
            tree = ui()
            if node(tree, text="#2 SIGN_MESSAGES") is not None:
                break
            adb("shell", "input", "swipe", "540", "1800", "540", "650", "250")
        else:
            raise AssertionError("Signing event absent from canonical detail timeline")
        if status == "FAIL":
            assert node(tree, text="Protocol error: ERROR_NOT_SIGNED (-3)") is not None
            assert node(tree, text="Failure source: OBSERVED_PROTOCOL") is not None
        else:
            assert node(tree, text="SUCCESS · "+str(max(timeline[1]["completed_at_ms"]-timeline[1]["started_at_ms"],0))+" ms") is not None
        (scratch/("restart-"+status.lower()+".png")).write_bytes(adb("exec-out", "screencap", "-p").stdout)
        checks.append({"gate": "restart-loaded-"+status+"-Sessions-and-Detail-UI", "result": "PASS",
                       "sessionId": session["session_id"], "eventCount": len(timeline),
                       "sessionSha256": hashlib.sha256(json.dumps(session,sort_keys=True).encode()).hexdigest(),
                       "timelineSha256": hashlib.sha256(json.dumps(timeline,sort_keys=True).encode()).hexdigest()})
        print("Restart-loaded "+status+" UI PASS", flush=True)
    evidence = {"serial": options.serial, "checks": checks, "database": "mwa_lab.db", "schemaVersion": 1,
                "getCapabilitiesObserved": False, "phase4Started": False}
    Path(options.evidence).write_text(json.dumps(evidence, indent=2, sort_keys=True)+"\n")
    adb("shell", "rm", "/sdcard/mwa-phase3-acceptance.xml")
    print("PHASE 3 CROSS-PACKAGE / PROCESS RESTART / UI ACCEPTANCE: PASS", flush=True)
    print("Screenshots and diagnostic database copies: "+str(scratch), flush=True)


if __name__ == "__main__":
    main()
