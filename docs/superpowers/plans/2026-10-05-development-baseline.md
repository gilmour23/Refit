# ReFit development baseline

The approved scope is to keep the Kotlin/Compose app and replace APK-only iteration with a reproducible repository, PC setup and device evidence. The current 0.5.3 patient flow is the baseline. Real sensor protocol and physical access remain external inputs.

## Global constraints

- Do not change patient behavior, database schema, authentication settings or app identity.
- Never publish local.properties, credentials, signing keys, patient data or generated APKs in Git.
- Preserve the existing remote branches. Publish a separate development branch based on the observed remote foundation head; no force push or main merge.
- Document the current mock source honestly. No invented sensor UUIDs, units, thresholds or clinical claims.

### Task 1: Establish and publish the development baseline

**Files:** Gradle wrapper, .github/workflows/android-ci.yml, README.md, docs/DEVELOPMENT.md, docs/DEVICE_TEST.md, docs/verification/2026-10-05-development-baseline.md.

**Interfaces:** The same Gradle wrapper and JDK 17 are used locally and by CI. Device evidence identifies the commit, build, data source and signing constraints. No consumer app interfaces change.

1. Check that ./gradlew is missing.
   Expected: command unavailable, confirming the reproducibility gap.
2. Generate the official Gradle 8.13 wrapper. Pin the binary distribution checksum and compare its wrapper JAR to the official Gradle checksum.
   Expected: both hashes match; ./gradlew --version reports 8.13 on JDK 17.
3. Update CI to run the wrapper, unit tests, lint and debug assembly with isolated fake configuration; preserve the database policy check.
   Expected: YAML parses, configured commands exist and no real backend credentials are included.
4. Replace the stale README and add PC/device setup, work order, sensor requirements and a device evidence template.
   Expected: documentation matches current code and all relative links resolve; no physical test is claimed.
5. Run wrapper-based unit tests, debug assembly and lint once. Check the diff, tracked-file exclusions and document the results.
   Expected: the existing 66 tests plus any reproduced regression pass; build and Lint pass; no credentials or build output tracked.
6. Commit; run one independent final review focused on setup portability, CI configuration, signing/data preservation and unknown sensor protocol. Fix important findings and verify the affected path.
7. Upload the complete source tree to a new private branch using the repository APIs, verify remote tree/content, and open a draft PR against the existing foundation branch.
   Expected: remote files and modes match the reviewed local tree; existing branch heads are unchanged.

## Review focus

Check clean-PC setup assumptions, Windows commands, CI behavior without real configuration, wrapper integrity, existing-install signing identity, record preservation, and the distinction between mock/unit-test evidence and a real sensor session. Ensure no external secret or patient data is included in the proposed tree.
