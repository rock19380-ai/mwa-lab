# Phase 8 UX and positioning design freeze

Predecessor: `d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1` (`phase7-sanitized-diagnostic-reports-2026-10-02`). This document freezes presentation choices only. Protocol, security, persistence, fault, simulation, and report authority remain with their existing implementations.

## Product and copy

MWA Lab is the **Mobile Wallet Adapter protocol debugger and deterministic failure simulator**. The first viewport must say **SOLANA DEVNET** and **NO REAL FUNDS**, show the selected fault state, and direct users to a recorded session. A fault is an **INTENTIONAL TEST CONDITION**, never evidence that a production wallet is defective. Diagnostic reports are sanitized. Final compatibility must be validated with real production wallets; none is implied by this UI.

Status vocabulary: `PASS`, `FAIL`, `ACTIVE`, `CANCELLED`, `UNKNOWN`, `PARTIAL`. Failure-source vocabulary: `INJECTED`, `OBSERVED_PROTOCOL`, `SIMULATION`, `RPC_NETWORK`, `LOCAL_PARSER`, `UNKNOWN`. Preserve the stored source and injected fault as independent values.

## Destination map

Five top-level destinations: Home, Sessions, Fault Lab, Lab Identity, Settings. Session Detail is a child of Sessions and Back returns there. The wallet callback approval activity is outside this navigation shell. A compact first-run onboarding surface is shown only by the main activity.

## Visual contract

Use explicit stable light and dark palettes without dynamic wallpaper color. Large title, medium heading, body, and technical label styles form the hierarchy. Monospace is reserved for addresses and protocol codes. Failure and active fault information precedes method, result, duration, child diagnostics, then secondary metadata. Color always accompanies a text label. Status badges, safety banners, section cards, diagnostic values, timeline rows, and intentional empty/error/loading states share the same visual grammar.

## Evidence hierarchy

Home projects the existing identity, fault-selection, and persisted-session state. Session cards expose dApp, status, time, duration, event count, protocol result, injected marker, and Devnet context. Session Detail leads with the failed event's method, protocol result, failure source, independent injected fault, and duration; the ordered timeline follows. Request and response summaries remain sanitized and subordinate. Transaction and simulation views remain child evidence; simulation PASS never guarantees submission.

## Accessibility and safety

Primary actions have visible labels and practical touch targets. Icon-only actions need content descriptions. Content scrolls at large font sizes, while approval decisions stay reachable. Public identity shows only an address; identity reset remains hidden pending an authorization-state proof. Settings persists only onboarding/theme choices and shows the fixed network, RPC, and report policy as read-only information. No UI control can configure network, security, protocol, or export authority.

## Five-second test

Without scrolling, Home must answer: what the app is, its MWA audience, Devnet-only and no-real-funds scope, whether a fault is selected, and where to inspect a session. No synthetic connection-readiness or production-wallet compatibility state is displayed.
