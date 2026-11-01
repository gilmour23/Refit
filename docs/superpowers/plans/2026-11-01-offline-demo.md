# Offline Demo Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Start, exercise and save local demo records when backend configuration is absent.
**Architecture:** Route missing configuration to a reserved local-only owner before creating auth state. Reuse the existing Room/mock-data flow and hide account/cloud controls for this route.
**Tech Stack:** Existing Kotlin, Compose, Room, Robolectric and GitHub Actions.
**Spec:** docs/superpowers/specs/2026-11-01-offline-demo-design.md

## Global Constraints
- Reserved local owner: `__offline_demo_v1__`.
- Keep existing encrypted record storage and configured-account login.
- No backend, secret, dependency, signing or real-sensor changes.

## Review Focus
- Configuration present must retain login and owner filtering.
- Offline records must remain separate from real account records.
- No sync controls or cloud-success claims in offline history.
- Activity recreation must not return an offline user to login.
- Storage failures must retain existing data rather than reset it.

### Task 1: Offline entry and local exercise
**Files:** MainActivity.kt; HomeScreen.kt; HistoryScreen.kt; OfflineDemoLaunchTest.kt; android-ci.yml.
**Interfaces:** Reuse CheckedAuthenticatedApp and account-scoped SessionViewModel; make logout optional for the local route and history synchronization controls conditional.
- [x] Add real-activity tests for unconfigured startup/recreation, owner isolation and saving/reopening a local mock exercise.
- [x] Run the unconfigured CI test against the old implementation; expect missing exercise action rather than a compilation failure.
- [x] Add missing-configuration routing and hide account/cloud actions in the local route.
- [x] Run offline integration tests, then the existing configured unit suite, debug lint/build and database ownership CI.
- [x] Review the complete changes and publish a reviewable branch with the pull/update instructions.
