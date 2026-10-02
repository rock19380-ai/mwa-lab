#!/usr/bin/env python3
"""Phase 7 Android export boundary source check, separate from frozen Phase 6 gates."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None
providers = [p for p in application.findall("provider")
             if p.get(android + "authorities") == "${applicationId}.diagnosticreports"]
assert len(providers) == 1
provider = providers[0]
assert provider.get(android + "name") == "androidx.core.content.FileProvider"
assert provider.get(android + "exported") == "false"
assert provider.get(android + "grantUriPermissions") == "true"
metadata = provider.findall("meta-data")
assert len(metadata) == 1
assert metadata[0].get(android + "name") == "android.support.FILE_PROVIDER_PATHS"
assert metadata[0].get(android + "resource") == "@xml/diagnostic_report_paths"
paths = ET.parse(root / "app/src/main/res/xml/diagnostic_report_paths.xml").getroot()
assert paths.tag == "paths"
assert len(paths) == 1
only = paths[0]
assert only.tag == "cache-path"
assert only.get("name") == "diagnostic_reports"
assert only.get("path") == "diagnostic_reports/"
share = (root / "app/src/main/java/dev/mwalab/report/DiagnosticReportShareIntentFactory.kt").read_text()
for required in ("FileProvider.getUriForFile", "Intent.ACTION_SEND", "Intent.EXTRA_STREAM",
                 "Intent.FLAG_GRANT_READ_URI_PERMISSION", "Intent.createChooser",
                 'uri.scheme == "content"', "file.canonicalFile.parentFile == directory",
                 "file.length() in 1..ReportLimits.MAX_RENDERED_BYTES"):
    assert required in share, required
assert "file://" not in share
for path in (root / "docs/evidence/phase7").rglob("*"):
    if path.is_file():
        data = path.read_bytes()
        for sentinel in (b"raw-auth-secret-SENTINEL", b"private-key-secret-SENTINEL",
                         b"seed-secret-SENTINEL", b"association-token-secret-SENTINEL",
                         b"raw-message-secret-SENTINEL", b"raw-transaction-secret-SENTINEL",
                         b"signature-secret-SENTINEL"):
            assert sentinel not in data, path
print("PHASE 7 EXPORT SECURITY STATIC CHECK: PASS")
