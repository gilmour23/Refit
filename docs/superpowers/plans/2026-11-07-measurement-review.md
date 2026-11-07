# Measurement Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect photo selection, correction, versioned review and partial retakes to real measurement outputs.
**Architecture:** Original decoded-image coordinates plus SVG selection; SQLite revision records and private per-revision files; existing bounded serial worker.
**Tech Stack:** FastAPI, SQLite, Python/OpenCV, vanilla JS, SVG.
**Spec:** docs/superpowers/specs/2026-11-07-measurement-review-design.md

## Global Constraints
Preserve all eleven definitions, scalar calibration, raw uploads, private admin assets, receipt secret verification and current visual direction. Maximum 20 revisions per case. No claimed accuracy validation.

## Review Focus
- Old version assets must never appear beside current-version values or be marked reviewed.
- Invalid, nonfinite, degenerate or out-of-bounds card/wrist points are rejected.
- Retaking one requested view preserves the other original and its applicable overrides.
- Reload restores only this tab's receipt; a new-person action clears private session state.
- Failed recomputation and restart keep previous results accessible.

### Task 1: Geometry
- [x] Write tests for original-coordinate serialization, finite points and manual wrist propagation; run RED.
- [x] Return `geometry.{top,side}` with dimensions, lines, wrist and card from measure_brace; retain metadata on failure.
- [x] Run geometry and boundary tests GREEN.

### Task 2: Private revisions and retakes
- [x] Write API/storage tests for stale revisions, auth, review gate, immutable originals/assets, partial retakes and migration; run RED.
- [x] Add revision storage, correction/review/retake-request endpoints and receipt-authorized retake endpoint. Worker reads per-revision corrections.json. Expose history and version-pinned assets.
- [x] Run Python suite GREEN.

### Task 3: Fabrication workflow
- [x] Write JS tests for scaled pointer coordinates, invalid geometry, edit cancellation and same-case version races; run RED.
- [x] Add SVG geometry selection, zoom, guided correction, revision/review/retake controls and state filters. Pin every asset/download/print to revision and session.
- [x] Run JS suite GREEN.

### Task 4: Participant and delivery
- [x] Write receipt restoration and partial-retake state tests; run RED.
- [x] Add final confirmation and partial-retake UI, tab-scoped receipt storage and forget action.
- [x] Run complete suites and visual review, update README and runnable ZIP. Final independent branch review and fix material findings.

## Verification and review

36 Python and 22 Node tests pass. Independent review found interrupted staging recovery, in-flight request revocation and undo/print state issues; each reproduced and fixed with regression coverage. UI fixture verified selection, pointer correction and v2 save; mobile 360/430 CSS frames had no horizontal overflow and exposed only requested side retake. Fixture is not a native inference run. Native accuracy and actual phone camera remain pending.
