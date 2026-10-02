# Phase 8 accessibility and layout audit — implementation checkpoint

Date: 2026-10-02. This is Phase 8 hardening evidence, not a freeze claim.

## Device/environment observed

- Android emulator: `emulator-5554`, `sdk_gphone64_x86_64`.
- Physical display reported during the audit: 1080×2424, density 420.
- Baseline system font scale observed before the large-font audit: `1.0`.
- Large-font audit scale: `1.3`.

## Concrete layout findings and repairs

The device review found three presentation risks and repaired them without moving protocol or signing authority:

1. The safety banner's `SOLANA DEVNET` and `NO REAL FUNDS` labels could crowd each other. The banner now uses a layout that can wrap safely rather than assuming a wide viewport.
2. Home identity/address actions could overflow. The Home layout now permits the action area to adapt rather than clipping the public-address workflow.
3. Settings theme choices had short tap targets. Theme choices now meet a minimum 48 dp target, with instrumented coverage.

The duplicate app-level title consumed vertical space on dense diagnostic screens, so the redundant top app bar was removed while keeping each destination's own title and the five-destination bottom navigation.

## Device observations at 1.3× font scale

The emulator review at 1.3× font scale observed:

- Home safety copy remained readable.
- Bottom navigation remained usable.
- Fault Lab kept the active-fault warning visible.
- `RETURN TO NORMAL` remained reachable.
- Light and dark palettes remained legible.
- Settings remained usable after theme switching.

A constrained Compose device test also exercises the signing approval surface at 360×640 dp with 1.3× font scale and dark theme. It requires all of the following to remain displayed/reachable:

- `SOLANA DEVNET`;
- `NO REAL FUNDS`;
- request fault banner;
- `FAULT ACTIVE`;
- `APPROVE`;
- `REJECT`.

## Accessibility semantics retained

- Status and fault meaning remains textual; color is supporting evidence only.
- Bottom-navigation destinations retain visible labels and content descriptions.
- Approval decisions remain explicit text actions.
- Theme choices have click semantics and a tested minimum target height.
- No protocol, signing, fault, simulation, persistence, or report authority was moved into Compose as part of this audit.

## Verification scope

Focused compile/unit/Compose checks executed during this hardening pass before token exhaustion and passed. The full Prompt-2 local and connected regression matrix remains a separate completion gate and must be rerun after all hardening files are finalized.
