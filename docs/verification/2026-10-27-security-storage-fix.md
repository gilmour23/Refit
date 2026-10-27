# Security storage remediation — ReFit 0.6.1-preview — 2026-10-27

This implements the confirmed findings in [the preceding audit](2026-10-27-security-audit.md). The application ID remains com.refit.app.preview; versionCode is 12. The original com.refit.app installation is not replaced. This is a patient-UI review build, not a completed physical orthosis integration.

## Implemented protections

- Session credentials are serialized and encrypted with AES-256-GCM under Android Keystore alias refit.auth.v1. No plaintext password is persisted. Legacy default-SDK sessions migrate only after encrypted storage is committed; encrypted loads retry any interrupted legacy cleanup.
- Logout removes the encrypted session and commits a durable signed_out marker atomically. The marker prevents a stale legacy account from returning after a kill before cleanup. Explicit successful session saves clear the marker. Blocking preference/key work runs on Dispatchers.IO.
- Local Room schema v5 stores complete clinical record payloads encrypted with AES-256-GCM under a separate Keystore key. Only local row IDs and stable opaque owner/client tags remain outside ciphertext. Tag integrity is checked against decrypted ownership/client identity, and the original per-owner client-ID uniqueness is preserved.
- v1/v3/v4 records migrate transactionally to v5. Each ciphertext round-trip is checked before the old table is dropped. secure_delete is enabled using a query (Android PRAGMA returns a row), free pages are vacuumed, and WAL cleanup is checked. Failed encryption rolls back to the original records.
- History and pending synchronization retain deterministic time/row-ID ordering, including tied timestamps. Read-modify-write symptom/sync updates are Room transactions; generated DAO wrappers were inspected after preserving the DAO interface.
- Missing keys do not regenerate during decryption. A previously observed key also cannot be silently recreated during encryption in the same process. Startup validates all encrypted rows before showing the authenticated app; storage faults retain records and show a blocking error. Later history faults block new exercises.
- New signup requires at least 12 characters in UI and repository. Existing six-character sign-in remains accepted. This changes this client's validation, not an independently verified server-side password policy.
- Preview signing backup is now a Fernet-encrypted archive with a separate recovery file. Decryption round-trip and wrong-key rejection passed; the underlying signing identity is unchanged. The prior unprotected archive was moved to trash, and its local deliverable copy was removed. Trash is recoverable, not a guarantee of permanent erasure.

## Test evidence

The two original regression tests were first run against the old implementation and failed on refresh-token plaintext and clinical-record plaintext. Later review regressions were written before their fixes, but their individual RED runs were blocked by a DAO compile-compatibility failure; they must not be described as independently observed RED runs.

The focused migration/DAO/synchronization run completed successfully: **21 tests, 0 failures, 0 errors, 0 skipped**. It covers old schema migration and ownership, on-disk marker absence, rollback on key failure, database reopen, duplicate client IDs, owner-tag tampering, same-owner ciphertext substitution, and synchronization behavior.

The final whole-unit-suite run completed: **92 tests across 20 classes, 0 failures, 0 errors, 0 skipped**. Full debug lint passed with **0 errors and 21 warnings** (dependency/update, target API, resources/icon and style findings; no security-specific lint finding). Final signed APK verification is pending below.

A fresh read-only review found interrupted session cleanup/logout risks; the implemented cleanup retry and durable marker are covered by passing EncryptedSessionManager tests from the first whole-suite run. That first run also exposed the PRAGMA API and tied-order regressions; these were corrected without weakening their assertions.

## Remaining boundaries

- Fresh Supabase advisor still returns one WARN: Leaked Password Protection Disabled. [Official remediation](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). The feature requires Pro or above; no plan purchase or auth-server configuration change was performed. Server-side minimum password/complexity settings were not independently established through the available connected capabilities.
- Guide settings still use private preferences, including an owner UUID in the filename and wrist side/timing values. Claims of encryption cover credentials and clinical record payloads, not all app metadata.
- Both encrypted signing backup and separate recovery file initially reside in the same private user account. A person who obtains both can restore the signing key. Keep the recovery secret in independently controlled storage; encryption alone does not defend compromise of that account.
- JVM tests use real AES-GCM and SQLite plus a test-only AndroidKeyStore provider. Real-device hardware/provider behavior, actual APK installation/login/update, malicious full-database replay, forensic flash erasure and large-history performance were not established by these checks.
- No dependency-wide vulnerability scan or historical secret scan was completed. Physical BLE authentication and motor safety remain outside this app storage fix.

## Deployment state

No merge or public-store release is performed by this task. The preview uses the existing dedicated signing identity and retains the review package ID for in-place updates of the preview app. Original-app local records remain in the original app; this preview does not retroactively encrypt the original app's stored credentials or records.
