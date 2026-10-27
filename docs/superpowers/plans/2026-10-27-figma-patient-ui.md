# ReFit approved Figma implementation

> Execute inline with superpowers:executing-plans; user authorized implementation on 2026-10-27.

**Goal:** Port the approved patient screens and add honest live mock EMG feedback.
**Architecture:** Retain Compose navigation, acquisition, account isolation and Room persistence. Reuse Material3 buttons and shared components; extend patient presentation only.
**Tech stack:** Kotlin, Compose Material3, Room, Robolectric/Roborazzi.
**Spec:** https://www.figma.com/design/IJiuyhOIjjRMYfbB4xb5H5 (approved, including EMG addition).

## Constraints
- Mock signal stays explicitly labelled demo; no recovery score or movement success claim.
- Real-sensor values have no calibrated display scale yet; never apply demo thresholds to them.
- Preserve pause, disconnect, extra rest, voice, side selection, account ownership and stored records.
- Match navy #16324F, teal #226D65, white surfaces and Noto Sans KR.
- Preserve usable actions and readable instructions with short displays and 2x text.

## Review focus
Paused/disconnected acquisition must clear the bar; NaN/Infinity must never reach progress semantics; no samples cannot imply detection; live mock changes must change feedback; large text must not hide pause/end.

## Tasks
- [x] 1. Restore verified source baseline; add Compose assertions for mock feedback and pause/disconnect state; document the blocked local-baseline attempts and clean remote full-suite evidence.
- [x] 2. Update shared tokens, home/preparation/session/history/result/settings and use original authored wrist asset from the approved design. Add presentation-only demo scaling with invalid/stale-state guards and test it.
- [x] 3. Support the three approved pain/fatigue choices while accepting legacy saved values. Run complete tests, debug APK build and lint; inspect native screenshots at normal/compact/2x font. Save changes in a separate GitHub branch/draft PR.

## Evidence and decisions
Source parent: cfd84d2a19b241b21b802b4a0113061aefec33ab. Figma context for attempt acquired; subsequent context calls blocked by Starter quota. Asset download endpoint returns HTML instead of SVG; use the exact SVG authored for this Figma file in /tmp/refit-figma/build.js, not a substitute or UI screenshot. Other screens use that same authored design source and the already inspected renders. No deployment or physical sensor/motor integration is part of this change.

Validation: clean remote CI run 40469698709 passed 78 tests, lint (0 errors/26 warnings), debug build, database ownership checks. Native render evidence is documented in docs/verification/2026-10-27-figma-patient-ui.md. Baseline-local and red-to-green checks were blocked by setup/locks and are not claimed.
