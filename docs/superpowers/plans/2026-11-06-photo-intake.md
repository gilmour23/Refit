# Photo Intake Implementation Plan

> For agentic workers: execute with superpowers:executing-plans.

Goal: QR participant intake and durable operator-selectable photo measurement results.
Architecture: existing FastAPI, SQLite metadata, private original/output files and one in-process bounded measurement worker. Static mobile intake and operator shells call authenticated same-origin APIs.
Tech stack: Python/FastAPI/SQLite; plain HTML/CSS/ES modules.
Spec: ../specs/2026-11-06-photo-intake-design.md

Constraints: original bytes/EXIF; existing 11 dimensions unchanged including ⑨; distinct upload/admin keys; no key in URLs for private assets; names are untrusted; no fake successful measurements; existing APK/BLE endpoints remain compatible.
Review focus: duplicate participant names must not overwrite; wrong receipt/admin keys must not expose another participant; queue contention must not lose uploads; restart/failure must remain visible; late operator responses must not display wrong person's images.

- [x] Add failing integration tests for durable upload/list/detail, original preservation, consent, receipt isolation, auth, failure/restart, queue limits, deletion and bad IDs.
- [x] Implement intake store/worker/API plus explicit admin route authorization; run API tests.
- [x] Build mobile instructions, camera/library upload and receipt progress; operator selection, color dimension table, private image blobs and downloads. Verify client race control.
- [x] Change launcher QR to intake, create separate private operator page; test admin key isolation. Update operator instructions.
- [ ] Verify all Python/Node checks and native runtime CI, review changes, update branch/draft PR and deliver a clean ZIP/model without any participant data or keys.

Verification: 23 Python tests and 8 Node tests pass locally; all three browser modules pass syntax checks. Fresh reviewer found stale prior-submission polling could overwrite a new receipt. Reproduction failed before the receipt identity guard; regression now passes with the full suite. SQLite connections explicitly close for Windows file cleanup. Browser preview connection was unavailable (cloud browser localhost refused, extension did not connect); physical mobile camera, successful valid-photo measurement, public tunnel and BLE remain unverified. Native inference/HTTP intake failure CI pending.
