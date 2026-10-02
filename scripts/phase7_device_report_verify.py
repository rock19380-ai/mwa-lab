#!/usr/bin/env python3
"""Compare app-private Phase 7 cache reports with persisted Room rows on a debug device.

Read-only. Copies the Room database into a temporary directory, reports only
sanitized classifications, and discards the database copy on exit.
"""
import argparse
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile

SENTINELS = (
    b"raw-auth-secret-SENTINEL", b"private-key-secret-SENTINEL",
    b"seed-secret-SENTINEL", b"association-token-secret-SENTINEL",
    b"raw-message-secret-SENTINEL", b"raw-transaction-secret-SENTINEL",
    b"signature-secret-SENTINEL",
)
NAME = re.compile(r"mwa-lab-([0-9a-f-]{36})-[0-9]{13,16}\.(md|json)$")


def adb(serial, *args, required=True):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True)
    if required and result.returncode:
        raise RuntimeError("ADB read failed: " + " ".join(args[:3]))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--session-id", action="append", required=True)
    args = parser.parse_args()
    wanted = set(args.session_id)
    names = adb(args.serial, "shell", "run-as", "dev.mwalab", "ls",
                "cache/diagnostic_reports").stdout.decode().splitlines()
    reports = {}
    for name in names:
        match = NAME.fullmatch(name)
        if not match or match.group(1) not in wanted:
            continue
        data = adb(args.serial, "exec-out", "run-as", "dev.mwalab", "cat",
                   "cache/diagnostic_reports/" + name).stdout
        assert len(data) in range(1, 1024 * 1024 + 1), "Report byte bound"
        assert not any(marker in data for marker in SENTINELS), "Sentinel in cache artifact"
        reports.setdefault(match.group(1), {})[match.group(2)] = data

    assert wanted <= reports.keys(), "Requested report identity absent"
    with tempfile.TemporaryDirectory(prefix="mwa-phase7-db-") as scratch:
        for suffix in ("", "-wal", "-shm"):
            result = adb(args.serial, "exec-out", "run-as", "dev.mwalab", "cat",
                         "databases/mwa_lab.db" + suffix, required=False)
            if result.returncode == 0:
                (Path(scratch) / ("mwa_lab.db" + suffix)).write_bytes(result.stdout)
        db = sqlite3.connect(Path(scratch) / "mwa_lab.db")
        db.row_factory = sqlite3.Row
        try:
            assert db.execute("PRAGMA user_version").fetchone()[0] == 3
            for session_id in sorted(wanted):
                artifacts = reports[session_id]
                assert set(artifacts) == {"md", "json"}, "Both formats required"
                report = json.loads(artifacts["json"])
                markdown = artifacts["md"].decode("utf-8")
                assert report["session"]["session_id"] == session_id
                assert "- Completeness: " + report["session"]["completeness"] in markdown
                assert "- Truncated: " + str(report["truncated"]).lower() in markdown
                session = db.execute("SELECT completed_at_ms FROM sessions WHERE session_id=?",
                                     (session_id,)).fetchone()
                assert session is not None
                assert report["session"]["completeness"] == (
                    "COMPLETE" if session["completed_at_ms"] is not None else "PARTIAL")
                persisted = list(db.execute(
                    "SELECT sequence,method,outcome,protocol_error_code,failure_source,"
                    "injected_fault_id FROM protocol_events WHERE session_id=? ORDER BY sequence",
                    (session_id,)))
                events = report["events"]
                assert len(events) == len(persisted), "Event count mismatch"
                for row, event in zip(persisted, events):
                    for db_key, json_key in (
                        ("sequence", "sequence"), ("method", "method"),
                        ("outcome", "outcome"), ("protocol_error_code", "protocol_error_code"),
                        ("failure_source", "failure_source"),
                        ("injected_fault_id", "injected_fault_id")):
                        assert row[db_key] == event[json_key], (session_id, db_key)
                    assert "Event %s: %s" % (row["sequence"], row["method"]) in markdown
                    if row["outcome"] == "FAILURE":
                        assert row["failure_source"] in markdown
                        if row["injected_fault_id"]:
                            assert row["injected_fault_id"] in markdown
                assert "## Security Notice" in markdown
                assert "## Reproduction Context" in markdown
                focus = next((event for event in events if event["outcome"] == "FAILURE"), None)
                if focus is None:
                    focus = next((event for event in reversed(events)
                                  if event["method"] != "DEAUTHORIZE"), None)
                if focus is None and events:
                    focus = events[-1]
                if focus is not None:
                    assert "- Method to compare: " + focus["method"].replace("_", "\\_") in markdown
                print("PASS", session_id, report["session"]["status"],
                      report["session"]["completeness"], len(events), "events",
                      "Markdown/JSON/Room parity")
        finally:
            db.close()


if __name__ == "__main__":
    main()
