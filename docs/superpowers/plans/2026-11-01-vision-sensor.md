# Photo measurement and sensor management implementation plan

> For agentic workers: execute inline with executing-plans, then independent whole-branch review.

Goal: replace the real-photo placeholder path with automatic measurements and add real sensor management.
Architecture: standalone JVM-testable HTTP client/parser and Compose measurement flow; existing GATT source plus discovery and SessionViewModel management.
Tech: existing Kotlin/Compose, Java HttpURLConnection, kotlinx JSON, Python FastAPI.
Spec: docs/superpowers/specs/2026-11-01-vision-sensor.md

## Global constraints
- Photos alone; no manual landmarks or card corners. Eleven finite positive dimensions required.
- No credentials or real server URL available; runtime URL setting, HTTPS in release, local HTTP allowed only debug.
- Preserve original byte stream. Explicit warnings. No false successes.
- Existing exercise demo and registered-brace flow remain usable. Firmware settings must be supplied explicitly.

## Review focus
- Missing dimensions, invalid reports and server errors must not register a real measurement.
- Photo replacement, back navigation and cancellation must not show an old result.
- Connection failure/disconnect/late callbacks must not show false connected state.
- Returning from sensor management must use chosen source, including saved exercise data_mode.
- Owner changes cannot expose another owner's measurement record.

## Task 1: Automatic photo measurement
- [ ] Add failing parser/HTTP tests (complete/incomplete/error/warnings, original bytes and no manual-coordinate fields).
- [ ] Run red on CI before implementing; implement BraceMeasurement and BraceMeasurementClient.
- [ ] Add PhotoMeasurementFlow for actual photos, original upload, report zoom, warnings and retry.
- [ ] Preserve explicit example flow; store complete measured records encrypted with owner scope and rollback.
- [ ] Verify parser/HTTP/store/Compose flow and existing offline tests.

## Task 2: Real sensor management
- [ ] Add tests for scanned device list, failed selection/permissions, and switching from sensor to demo without fake state.
- [ ] Implement BleSensorScanner and direct selected-device GATT/RSSI handling.
- [ ] Add SensorScreen and connect/disconnect/demo management to SessionViewModel; use raw INT16_LE only after acknowledgement.
- [ ] Wire Home and exercise-preparation entry; preserve chosen mode through reset and existing timing/signal protection.
- [ ] Verify focused sensor tests, full suite, lint and APK.

## Task 3: Package and verify
- [ ] Include supplied Python service plus deployment/run documentation and focused validation fixes; no public deployment.
- [ ] Independent reviewer checks whole diff; fix material findings.
- [ ] Confirm latest exact-commit CI and screen artifacts before reporting completion.
