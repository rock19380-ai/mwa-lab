# Phase 9 Remote MWA release decision

Date: 2026-10-04

Status: **BLOCKED_HIDDEN**. The Remote MWA QR scanner is **OMITTED**.

The pinned walletlib 2.0.7 artifact contains `RemoteAssociationUri` and
`RemoteWebSocketServerScenario`. This proves API availability only. No usable
Remote counterparty/reflector URI, actual Remote session, authorization, sign
path, fault run, timeout/cancel cleanup, or subsequent Local regression receipt
exists for this release candidate. The Phase 9 end-to-end release gate is unmet.

The release UI has no `PASTE REMOTE MWA URI` or `SCAN REMOTE MWA QR` control.
The app requests no `CAMERA` permission and adds no scanner dependency. Local
MWA remains the available connection path. Room schema 4 and reports hold only
coarse association/verification metadata; raw Remote association material is
not a diagnostic field. Remote compatibility is **BLOCKED / NOT RELEASED**.
