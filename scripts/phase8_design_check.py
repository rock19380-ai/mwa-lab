#!/usr/bin/env python3
"""Phase 8 positioning and presentation contract check."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text()


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


design = source("docs/phase8-world-class-ux-positioning-design.md")
for phrase in (
    "Mobile Wallet Adapter protocol debugger and deterministic failure simulator",
    "SOLANA DEVNET", "NO REAL FUNDS", "INTENTIONAL TEST CONDITION",
    "PASS", "FAIL", "ACTIVE", "CANCELLED", "UNKNOWN", "PARTIAL",
    "INJECTED", "OBSERVED_PROTOCOL", "SIMULATION", "RPC_NETWORK", "LOCAL_PARSER",
    "Home, Sessions, Fault Lab, Lab Identity, Settings",
    "Session Detail is a child of Sessions",
    "simulation PASS never guarantees submission",
    "identity reset remains hidden",
):
    require(phrase.lower() in design.lower(), f"Phase 8 design decision missing: {phrase}")

theme = source("app/src/main/java/dev/mwalab/ui/theme/Theme.kt")
require("lightColorScheme(" in theme and "darkColorScheme(" in theme,
        "Explicit light and dark palettes missing")
require("dynamicLightColorScheme" not in theme and "dynamicDarkColorScheme" not in theme,
        "Dynamic color replaced the stable Phase 8 palette")

strings = {item.get("name"): item.text or "" for item in
           ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot().findall("string")}
require("Mobile Wallet Adapter protocol debugger" in strings.get("product_subtitle", ""),
        "Product identity resource missing")
for key, phrase in (
    ("devnet_only", "DEVNET ONLY"),
    ("solana_devnet", "SOLANA DEVNET"),
    ("no_real_funds", "NO REAL FUNDS"),
    ("fault_active", "FAULT ACTIVE"),
    ("intentional_test_condition", "INTENTIONAL TEST CONDITION"),
    ("sanitized_report", "Sanitized diagnostic report"),
):
    require(phrase in strings.get(key, ""), f"Phase 8 safety resource missing: {key}")
print("PHASE 8 DESIGN CHECK: PASS")
