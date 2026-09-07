# STATIC / DYNAMIC implementation

Branch: `feature/static-dynamic-scene-policy`.
Specification: [handoff](static-dynamic-scene-identity-az-lock-v3.md).

## Plan

- [x] Separate static evidence interpretation from dynamic continuity; preserve legacy wire names.
- [x] Centralize the hard presentation boundary; invalidate all asynchronous old-scene results.
- [x] Implement bounded static baseline, one AZ refinement per entity, and STATIC_IDLE.
- [x] Arm fixed vehicle/plate watch regions at base zoom; confirm local/global scene changes with a lightweight luma watcher.
- [x] Complete entity-owned manual lock and search verification/pursuit using ModeController.
- [x] Gate AZ by scene policy, geometry, normal MZ, acquisition phase, motion, and per-scene/per-lock budgets.
- [x] Freeze the selected analysis mode in research configuration and export semantic analysis_mode alongside legacy fields.
- [x] Run automated acceptance coverage S1–S12, D1–D3, M1, C1, H1–H2, R1–R2, AZ1–AZ8, L1–L7 and applicable existing regression suites; distinguish camera evidence in the report.
- [x] Install and verify the final application, commit, and record evidence and limitations.

## Preserved baseline

The AZ button/viewport update preceding this handoff is committed separately on this branch.
Keep gallery/history semantics, sample verification, model/runtime contracts, monotonic IDs,
confidence retention, slow-MP queue admission, and existing continuity generations/barriers.
No push is part of this handoff unless requested.

## Validation log

519 unit tests passed, without failures, errors or skips. The required Android regression selection passed 73 tests on SM-A125F.
Camera evidence covers static baseline/AZ/idle, local scene boundary, dynamic manual entity lock through changing track IDs, and search input/possible verification UI.
Detailed evidence and limits: [implementation report](implementation-report-v3.md).
