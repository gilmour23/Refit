# Secure local storage implementation plan

**Goal:** Fix the four confirmed audit findings without losing existing records or changing approved patient UI.
**Spec:** docs/verification/2026-10-27-security-audit.md
**Architecture:** AES-256-GCM encrypted sessions and full rehabilitation-record payloads, with keys in Android Keystore. Room stores only an opaque owner tag, local row ID and encrypted payload. Schema v5 migrates old columns transactionally, removes plaintext rows, and vacuums/checkpoints old pages. No new native dependency.
**Constraints:** Keep com.refit.app.preview and its existing signer for update installation. Keep all original app data. No paid-plan upgrade or server-secret exposure. Fail closed on corrupt ciphertext or unavailable keys; never silently replace keys or clear patient records.
**Review focus:** interrupted migration; missing/invalidated key; corrupt payload; account switching during writes; old plaintext sessions returning after logout.

## Task 1 — Session storage
- [x] Add a regression assertion that stored credentials do not contain token plaintext; observe current failure.
- [x] Add Android Keystore AES-GCM cipher and encrypted SessionManager; migrate old SDK preferences only after committed encrypted save.
- [x] Verify persistence, migration, tampering, wrong key/purpose, deletion, offline logout and account race behavior.

## Task 2 — Record storage
- [x] Add a regression checking DB/WAL bytes do not contain an inserted clinical marker; observe current failure.
- [x] Serialize complete records inside AES-GCM payload; keep repository API stable using owner-scoped DAO wrappers and provided Room converter.
- [x] Migrate v1/v3/v4 records to v5 in a Room transaction. Scrub/vacuum old pages and checkpoint WAL before admitting the DB to UI.
- [x] Verify migration preserves all record fields and owner boundaries, file reopen, encrypted bytes, rollback on crypto failure and updates.

## Task 3 — Auth and signing backup
- [x] Require at least 12 characters on new signup only; preserve existing-account sign-in. Do not imply this changes unverified server policy.
- [x] Replace insecure signing backup with an encrypted archive and separately retained recovery secret; retain same signing identity. Remove the old unprotected backup after verified durable replacement.
- [x] Recheck server advisor; report plan-dependent feature if unavailable.

## Task 4 — Delivery
- [x] Run complete unit suite, lint and signed preview build. Review changed code and package independently of implementation steps.
- [x] Verify version increment, signature continuity, no private key/server secret in APK and persistent download delivery.

## Execution ledger
- Baseline: audit run passed 32 auth/account/sync tests. Current directory is a task-specific scratch checkout on chore/figma-patient-ui, with previous approved UI changes not represented by its synthetic local git baseline. Preserve those changes; compare explicit file list with remote branch and update via GitHub leased ref.
- Decision: encrypted record payloads avoid adding an untestable native database layer in this environment; metadata (row ID/opaque owner tag) remains visible, clinical fields do not. Actual hardware-backed Keystore behavior remains a device check.

- RED confirmed: original password-login persistence test exposed refresh-token plaintext; original clinical file scan exposed plain record values. Log /tmp/refit-security-red.log (both failed as expected).
- Storage implementation first compile found Kotlin mixed array inference; fixed with explicit arrayOf<Any>; next selected test run is in progress.
- Backup task: original archive encryption/round-trip and wrong-key rejection passed. Encrypted backup and separate recovery file durably saved; old archive moved to trash and local raw archive removed. Trash is recoverable, not permanent erasure; both new files share private account access until user moves recovery secret separately.
- Fresh read-only review confirmed interruption risks in session cleanup/logout. Added RED regression tests for leftover legacy cleanup and durable logout marker; fix pending current test result.
- Review decisions: retain existing nonsensitive guide preferences for now and document raw account ID/side/timing metadata limitation; cryptographic protection claims cover session credentials and clinical record payloads. Harden live missing-key behavior and preserve owner/client uniqueness with integrity-checked opaque tags before delivery.

- Compatibility correction: original DAO is an interface and an existing sync race test decorates it by delegation. Preserve that interface rather than deleting the test or weakening its behavior. First selected run stopped on this compile issue.
- Review fix pass: logout tombstone, encrypted-load cleanup retry, live missing-key guard, owner/client unique tags plus substitution checks, and new-signup length policy implemented before the next full suite. Additional review regressions were written first but their individual RED runs were blocked by the compile compatibility failure; do not claim an observed RED for them. The two original security regressions did run RED.

- Full suite first executed: 87 tests / 4 failures. AuthAccountScope 1, AuthLogout 3, AuthLogoutRace 2, EncryptedSessionManager 5, DeviceCipher 2 and AuthRepository 7 all passed. RecordEncryptionTest initialization failed because a runBlocking test returned Boolean; corrected to Unit.
- Confirmed migration failure: Android rejects execSQL for PRAGMA secure_delete because the statement returns a row. Replaced with query and verified returned mode must be 1.
- Confirmed sync-order regression: pending rows tied on createdAt used opaque-index order; added explicit id tie-breakers, retaining insertion order for sync and newest ID for history. No assertions weakened. Selected migration/DAO/sync tests now running in /tmp/refit-security-migration-tests.log (session 61719).

- Focused regression run: BUILD SUCCESSFUL, 21/21 passed. Generated final interface DAO wrappers confirmed performInTransactionSuspending for symptom and sync updates.
- Final whole suite now completed: 92/92 passed (20 classes, zero failures/errors/skipped). Lint and signed preview generation remain in progress under parent session 84071. No more production source changes planned.

- Final full suite + debug lint: BUILD SUCCESSFUL in 10m 58s, 92/92 tests; lint 0 errors / 21 warnings. Preview compilation ongoing; paid leaked-password setting remains explicitly unresolved.

- Final preview APK built successfully, v2 signer continuity verified, real publishable client config checked and no test provider/key material bundled. APK delivery replacement succeeded (current version 1, ReFit-0.6.1-preview.apk); physical-device install/login/update remains unverified.

- Delivery: persistent APK replaced successfully after validation; original preview signing identity retained. Source committed at 52de3be81bfbd016ba71db123c3f32670adab802; draft PR #6 updated. Documentation-only evidence update does not change the tested/built production code.
