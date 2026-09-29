#!/usr/bin/env python3
"""Public unsigned vectors with independent expected structure; --check never writes."""
import argparse
import hashlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "test-vectors/transactions"
ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
SYSTEM = "11111111111111111111111111111111"
MEMO = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

def key(text):
    n = 0
    for char in text:
        n = n*58 + ALPHABET.index(char)
    result = bytes(len(text)-len(text.lstrip("1"))) + n.to_bytes((n.bit_length()+7)//8, "big")
    assert len(result) == 32
    return result

def base58(raw):
    n, text = int.from_bytes(raw, "big"), ""
    while n:
        n, digit = divmod(n,58)
        text = ALPHABET[digit]+text
    return "1"*(len(raw)-len(raw.lstrip(b"\0")))+text

def vector(accounts, refs, data, v0=False, invalid=False):
    message = (b"\x80" if v0 else b"") + bytes([1,0,1,len(accounts)])
    message += b"".join(accounts) + bytes([0x66])*32
    message += bytes([1,9 if invalid else len(accounts)-1,len(refs),*refs,len(data)]) + data
    if v0:
        message += bytes([1]) + bytes([0x66])*32 + bytes([1,7,0])
    return bytes([1])+bytes(64)+message

def outputs():
    payer, dest = bytes([0x11])*32, bytes([0x22])*32
    transfer = bytes([2,0,0,0])+(10_000_000).to_bytes(8,"little")
    cases = [
        ("legacy-memo",[payer,key(MEMO)],[],b"MWA Lab Phase 4 deterministic memo","MEMO"),
        ("legacy-system-transfer",[payer,dest,key(SYSTEM)],[0,1],transfer,"SYSTEM_TRANSFER"),
        ("legacy-unknown-program",[payer,dest,bytes([0x55])*32],[0,1],bytes([1,2,3]),"UNKNOWN"),
        ("legacy-spl-transfer",[payer,dest,bytes([0x33])*32,key(TOKEN)],[1,2,0],bytes([3])+(1_000_000).to_bytes(8,"little"),"SPL_TRANSFER"),
        ("legacy-spl-transfer-checked",[payer,dest,bytes([0x33])*32,bytes([0x44])*32,key(TOKEN)],[1,2,3,0],bytes([12])+(1_000_000).to_bytes(8,"little")+bytes([6]),"SPL_TRANSFER_CHECKED"),
        ("malformed-program-index",[payer,dest,key(SYSTEM)],[0,1],transfer,None),
        ("truncated-instruction-data",[payer,dest,key(SYSTEM)],[0,1],transfer,None),
        ("v0-unresolved-lookup",[payer,dest,key(SYSTEM)],[0,3],transfer,"UNAVAILABLE"),
    ]
    result, expected = {}, {"cases":",".join(c[0] for c in cases)}
    for name, accounts, refs, data, decoded in cases:
        v0 = name.startswith("v0")
        raw = vector(accounts,refs,data,v0,name.startswith("malformed"))
        if name.startswith("truncated"):
            raw = raw[:-1]
        result[name+".hex"] = raw.hex()+"\n"
        meta = dict(wire_length=str(len(raw)),fingerprint_sha256=hashlib.sha256(raw).hexdigest(),
            version="V0" if v0 else "LEGACY",status="PARTIAL" if v0 else "PARSED",signature_count="1")
        if decoded is None:
            meta.update(status="MALFORMED",fee_payer="unavailable",account_count="unavailable",
                instruction_count="unavailable",required_signer_count="unavailable",
                error="PROGRAM_INDEX_OUT_OF_RANGE" if name.startswith("malformed") else "TRUNCATED_INSTRUCTION_DATA",
                error_offset="199" if name.startswith("malformed") else "203")
        else:
            meta.update(fee_payer=base58(payer),required_signer_count="1",account_count=str(len(accounts)),
                instruction_count="1",recent_blockhash=base58(bytes([0x66])*32),
                program_id=base58(accounts[-1]),references=",".join(map(str,refs)),
                data_length=str(len(data)),data_sha256=hashlib.sha256(data).hexdigest(),decoded=decoded,
                account_keys=",".join(map(base58,accounts)),
                account_signers=",".join("true" if i==0 else "false" for i in range(len(accounts))),
                account_writable=",".join("false" if i==len(accounts)-1 else "true" for i in range(len(accounts))))
            if decoded=="SYSTEM_TRANSFER":
                meta.update(decoded_from=base58(payer),decoded_to=base58(dest),lamports="10000000")
            if decoded=="MEMO":
                meta.update(memo_status="DISPLAYABLE")
            if decoded.startswith("SPL_"):
                meta.update(source=base58(accounts[1]),destination=base58(accounts[3 if decoded.endswith("CHECKED") else 2]),
                    authority=base58(payer),raw_amount="1000000")
                if decoded.endswith("CHECKED"):
                    meta.update(mint=base58(accounts[2]),declared_decimals="6")
            if v0:
                meta.update(total_account_count="4",unresolved_reference="3",decoding_reason="UNRESOLVED_ACCOUNTS")
        expected.update({name+"."+field:value for field,value in meta.items()})
    result["expected.properties"] = "# Independent expected diagnostics; see README.md.\n"+"\n".join(
        field+"="+value for field,value in sorted(expected.items()))+"\n"
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--write",action="store_true")
    action.add_argument("--check",action="store_true")
    args = parser.parse_args()
    for name,content in outputs().items():
        target = ROOT/name
        if args.write:
            ROOT.mkdir(parents=True,exist_ok=True)
            target.write_text(content,encoding="ascii")
        else:
            assert target.read_text(encoding="ascii")==content, "Fixture drift: "+name
    print("PHASE 4 DETERMINISTIC VECTORS: PASS")

if __name__=="__main__":
    main()
