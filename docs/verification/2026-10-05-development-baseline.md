# Development baseline verification

## Scope

Reproducible setup and repository handoff for the existing 0.5.3 Kotlin/Compose app. No change to patient screens, authentication, database schemas or the default mock source. The new Lint gate exposed existing BLE permission-revocation paths and an API-level resource issue; these are corrected as part of the baseline.

## Evidence

- Initial check: `./gradlew` was absent.
- Generated the canonical Gradle 8.13 scripts and wrapper JAR; executable mode is retained for `gradlew`.
- Official distribution SHA-256: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Official wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- The already downloaded distribution ZIP matched the official checksum and was staged in the local Wrapper cache. This checks wrapper bootstrap and execution; it does not claim a fresh network download on another PC.
- Wrapper version check: Gradle 8.13, launcher JDK 17.0.16.
- Full fresh wrapper command: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -Pkotlin.incremental=false --rerun-tasks`, using a workspace-only proxy/JDK launcher.
- Initial wrapper run: 66 tests and debug assembly passed; Lint failed with six BLE MissingPermission errors and one API-27 theme attribute in the API-26 resource set.
- Added a permission-revocation regression: the old scan callback threw SecurityException (RED). Async BLE calls now explicitly handle revoked permissions, and the navigation-bar attribute is scoped to API 27+. Final wrapper command exited 0: **67 tests, 0 failures, 0 errors, 0 skipped; debug assembly and Lint passed (0 errors, 13 existing warnings).**
- CI YAML parses, documentation links resolve, executable mode and ignored configuration/signing/log/build paths are checked.
- CI uses a fake backend configuration and read-only repository permissions. No live database setting is changed.
- Kotlin 2.4 emits an existing Gradle deprecation warning: Kotlin 2.5 will require at least Gradle 8.14.4. Current versions are retained for this verified baseline; the next Kotlin upgrade must update Gradle too.

 Physical PC, phone, voice and sensor checks remain unexecuted; use [the device evidence template](../DEVICE_TEST.md).

## Repository handoff

The connected private repository is `gilmour23/Refit`. The observed foundation head is `7c15ad0775b70d3fc18b90e86aea5dc220923adb`. Recent patient-flow commits exist only locally, so the new branch must include the complete 0.5.3 source tree and setup changes. Existing main and foundation branches are preserved.

## Remote verification

- Published a separate private development branch and draft PR #4; all 95 tracked blobs and modes matched the local Git tree. Existing seven branch heads were unchanged.
- First Actions run passed PostgreSQL ownership checks but failed before Android build: `sdkmanager: command not found` (exit 127). Added an explicit Android SDK setup action instead of relying on runner PATH. Final CI result is recorded after the rerun.
- Independent review found no Critical or Important source issues. Windows BAT uses canonical CRLF; whitespace-only normalization is deferred. Physical sensor, Windows execution, Korean speech and existing-device updates remain pending.
