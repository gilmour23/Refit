# Patient Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the approved wrist attempt flow understandable and operable with one hand, optional audio, and rest support.

**Architecture:** Keep the existing account-scoped SessionViewModel and Room repository. Add READY and signal interruption handling to session state, account-local side/audio preferences, stepwise preparation UI, and optional symptom metadata. Do not alter Supabase schema or connection configuration.

**Tech Stack:** Kotlin, Compose, coroutines, Room, Robolectric/Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-05-patient-flow-design.md`

## Global Constraints
- Three seconds of READY are excluded from timed guidance and sample summaries.
- Keep mock provenance explicit; never infer movement or recovery from signal amplitude.
- Keep existing signing identity and account ownership boundaries.
- New metadata is device-local; no external database changes.

## Review Focus
- Stale sample and disconnected sensor must not silently advance a measured session.
- Background pause during connection/READY must remain paused until explicit resume.
- Extra rest must not add guidance count or active elapsed time.
- Old Room rows must survive migration with nullable symptom/side fields.
- Large text must leave instruction, pause, and end controls reachable.

### Task 1: Patient flow across state, storage and screens

**Files:** SessionViewModel.kt, GuideTiming.kt, SessionScreen.kt, new SessionVoice.kt and WristIllustration.kt; HomeScreen.kt, ResultScreen.kt, HistoryScreen.kt, ReFitUi.kt, MainActivity.kt; Room entity/model/DAO/database/repository; GuidedSessionTest.kt, PatientScreensTest.kt, RehabSessionDaoTest.kt; app/build.gradle.kts.

**Interfaces:** `configureWristSide(String): Boolean`, `setVoiceEnabled(Boolean)`, `extendRest()`, `recordSymptoms(pain: String?, fatigue: String?)`; state adds side, READY seconds, signal status, voice preference; nullable Room/result fields side, pain and fatigue.

- [x] Write assertion-failing preparation UI, READY and disconnect tests against current code; run selected tests and inspect failures.
- [x] Add account-local preferences, READY/hold/interruption state, owner-scoped symptom persistence and v3→v4 migration. Update existing test fixtures for three-second preparation.
- [x] Implement one-step preparation, direction illustration, signal status, extra rest, optional symptom choices and voice support/replay; update real UI paths and screenshots.
- [x] Add persistence/migration, stale-signal and large-text checks; run `python3 /tmp/refit-tools/run-gradle.py :app:testDebugUnitTest :app:assembleQa -Pkotlin.incremental=false` (expected: all tests pass, QA build succeeds).
- [x] Inspect rendered screens, commit, obtain one independent branch review, fix material findings, verify APK packaging and upload/download byte equality.

Completion evidence: `docs/verification/2026-10-05-patient-flow-053.md`. Review fixes were tested RED then GREEN; the final suite has 66 passing tests. A shorter 411×640 dp viewport with 2× text is covered in addition to the tall preview.
