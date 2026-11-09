# Simple maker and QR welcome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Apply the approved single-screen maker and improve the laptop QR welcome.
**Architecture:** Keep the existing HTML/CSS/modules and Python laptop launcher. Remove stage state from the maker, retain server APIs and corrections, and render a self-contained welcome template from the launcher.
**Tech Stack:** HTML, CSS, JavaScript modules, Python, existing QR library and FastAPI.
**Spec:** docs/superpowers/specs/2026-11-09-simple-maker-design.md

## Global Constraints
- No measurement algorithm, eleven definitions, original uploads, keys, schema or hosting changes.
- Approval UI and workflow stages are removed. Historical versions remain read-only.
- No participant data or real credentials in fixtures, previews, commits or delivery ZIP.

## Review Focus
- Delayed photograph requests after switching cases must not expose stale photographs.
- Pending corrections must block computed downloads and incomplete saves.
- Historical versions and processing cases must not allow mutation.
- Actual QR payload must still open the participant receipt flow; administrative keys never enter that screen.
- Small laptop and narrow mobile viewports must remain usable, with tools closed initially.

### Task 1: Persistent maker workspace
**Files:** vision/web/operator.html, maker.css, operator.js, workbench.mjs; vision/tests/operator-record.test.mjs, operator-simple.test.mjs, operator-harness.mjs, workbench.test.mjs.
**Interfaces:** Existing record/result/geometry/overrides and admin endpoints. Photo tabs continue using original pixel coordinates; no stages remain.
- [x] Add behavioral tests for immediately visible dimensions, inline corrections, saved-original retry, readonly/busy guards and stale-image isolation. Observe the new expectations fail against the original UI.
- [x] Replace stage UI/state with closed native menus and an inline correction panel; retain downloads, history, retake and deletion.
- [x] Run all Node tests, inspect CSS for desktop/narrow layouts (browser connection unavailable) and update the existing stale-image tests to the new download-based interface.

### Task 2: Laptop QR welcome
**Files:** scripts/laptop.py, scripts/join.html, vision/tests/test_laptop.py, README_DEMO_KO.md.
**Interfaces:** join_page(url, upload) still writes DEMO_JOIN.html and DEMO_CONNECTION.txt; run() and operator_page() stay compatible.
- [x] Add a real rendered-output test: connection key is absent from default-visible text, actual embedded QR decodes to the exact participant URL, and images work without external assets. Observe the visible-key assertion fail on the old page.
- [x] Render a local template with escaped URLs, a large QR, existing illustration and collapsed connection details. Update instructions.
- [x] Run Python and Node suites, verify real browser layout and QR scan after screenshot sizing, package current runtime/model without private files, update the existing branch/ZIP and report validation limits.
