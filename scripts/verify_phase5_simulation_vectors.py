#!/usr/bin/env python3
"""Independently reconstruct public Phase 5 template bytes and check RPC fixtures."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VECTORS = ROOT / "test-vectors/simulation"
ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
PAYER = bytes([0x11]) * 32
BLOCKHASH = bytes([0x66]) * 32


def base58(value):
    number = 0
    for char in value:
        number = number * 58 + ALPHABET.index(char)
    body = number.to_bytes((number.bit_length() + 7) // 8, "big")
    return bytes(value.startswith("1") and len(value) - len(value.lstrip("1")) or 0) + body


def wire(message):
    return bytes([1]) + bytes(64) + message


def vectors():
    memo_program = base58("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr")
    assert len(memo_program) == 32
    memo = b"MWA Lab Phase 5 simulation acceptance"
    good_message = bytes([1, 0, 1, 2]) + PAYER + memo_program + BLOCKHASH + bytes([1, 1, 0, len(memo)]) + memo
    bad_message = bytes([1, 0, 1, 2]) + PAYER + bytes(32) + BLOCKHASH + bytes([1, 1, 0, 4, 255, 255, 255, 255])
    return {"legacy-good-memo-template": wire(good_message),
            "legacy-bad-system-template": wire(bad_message)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    options = parser.parse_args()
    expected = {}
    for name, data in vectors().items():
        assert 1 <= len(data) <= 1232
        assert data[1:65] == bytes(64)
        path = VECTORS / (name + ".hex")
        encoded = data.hex() + "\n"
        if options.write:
            path.write_text(encoded)
        assert path.read_text() == encoded, name + " changed"
        expected[name + ".wire_length"] = str(len(data))
        expected[name + ".sha256"] = hashlib.sha256(data).hexdigest()
    properties = "".join(key + "=" + expected[key] + "\n" for key in sorted(expected))
    if options.write:
        (VECTORS / "expected.properties").write_text(properties)
    assert (VECTORS / "expected.properties").read_text() == properties
    for name in ("rpc-pass", "rpc-fail", "rpc-custom", "rpc-blockhash", "rpc-unknown", "rpc-unavailable"):
        fixture = json.loads((VECTORS / (name + ".json")).read_text())
        assert fixture["jsonrpc"] == "2.0" and fixture["id"] == 1
        assert ("result" in fixture) != ("error" in fixture)
        if "result" in fixture:
            assert fixture["result"]["context"]["slot"] == 52
            assert "err" in fixture["result"]["value"]
    assert json.loads((VECTORS / "rpc-pass.json").read_text())["result"]["value"]["err"] is None
    assert json.loads((VECTORS / "rpc-blockhash.json").read_text())["result"]["value"]["err"] == "BlockhashNotFound"
    print("PHASE 5 DETERMINISTIC SIMULATION VECTORS: PASS")


if __name__ == "__main__":
    main()
