# Maker Workbench Implementation Plan

> **For agentic workers:** implement inline using superpowers:executing-plans.

**Goal:** Make actual fabrication review operate in a persistent photo workbench.
**Architecture:** Reorganize existing DOM IDs and APIs; isolate maker CSS from participant layout; small pure fit/step helpers.
**Tech Stack:** HTML/CSS, SVG, ES modules, existing FastAPI.
**Spec:** ../specs/2026-11-07-maker-workbench-design.md

## Global Constraints
Preserve 11 definitions, current version review, raw files, auth, revision downloads, unsaved edit/print guards and participant UI. UI examples contain no real patient photographs or numerical measurements.

## Review Focus
- Fitted SVG and original image bounds coincide at fit and zoom, including tall/wide photos.
- Switching stages preserves corrections and does not bypass review/print guards.
- Failed and legacy records retain a discoverable recalculation and retake action.
- Desktop photo, toolbar and primary task action remain visible together; no clipped hand/wrist.
- Print exposes both photos and all eleven values irrespective of chosen stage.

### Task 1: Workbench behavior
- [x] Write RED tests for full-image fitting, step navigation and draft-preserving stage changes.
- [x] Add pure fit/step helpers, stage navigation and fitted image viewport integration.
- [x] Run focused Node tests GREEN.

### Task 2: Reference-driven layout
- [x] Rebuild operator.html around the persistent canvas, contextual inspector and task action bar; retain event IDs.
- [x] Add maker.css and exact public assets; compact list, table, controls and error/empty states.
- [x] Run full Python/Node tests, syntax/HTML/CSS checks and inspect actual responsive browser rendering.

### Task 3: Delivery
- [x] Review the complete change independently; fix material findings with reproductions.
- [x] Capture photo-check and dimension-review screens with labeled illustration fixtures; show source-to-design mapping.
- [x] Update README, existing ZIP/screens and draft PR without merging or deploying.

## Verification

36 Python + 25 Node tests pass. Added tests watched RED then GREEN for fit geometry, stage draft preservation, correction input suspension outside Photo, pressed-tool consistency and print/review guards. HTML IDs/JS targets, JS syntax and CSS parse pass. Independent review found no remaining material issue after the stage input/style fixes.

Browser fixture at 1363x936: image and SVG bounds exactly equal (341.25x455 at x444.375/y354.75), body width equals viewport, full hand/card visible, primary action inside viewport. Card selection reaches 4/4 and enables persistent save action; cancellation restores old result; all eleven dimension labels and selection are visible; retake dialog validates missing input in the dialog. Fixture uses an illustration and blank measurements and is excluded from source/delivery. Actual phone camera, native accuracy and physical printing are not newly validated.
