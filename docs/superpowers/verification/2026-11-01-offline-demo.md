# Offline demo verification

Implementation tested: `d5608ec0eff59ea14b6028628541d9ef2658c14d`.
Base: `ab0039ce55e331144c3d74eac08d117a1bc196f3`.

## Regression evidence

- [Original implementation run](https://github.com/gilmour23/Refit/actions/runs/41278249527): the three new real-activity tests compiled, then failed while waiting for the exercise action on the login-only root. All three failures were Compose timeouts rather than compilation failures.
- [Implementation run](https://github.com/gilmour23/Refit/actions/runs/41278660250): both jobs passed. The empty-configuration integration step passed, followed by the existing configured unit suite, `:app:lintDebug`, and `:app:assembleDebug`. The database schema/ownership/idempotent-upsert job also passed. Build output contains existing Gradle/Kotlin deprecation warnings; no failed check was reported.

The offline integration suite covers startup without backend settings, activity recreation, hiding account/cloud actions, separation from another owner's records, and saving/reopening a mock exercise through the real activity and encrypted Room path. The test application supplies the existing test AndroidKeyStore implementation.

## Delivery

```powershell
git -c http.version=HTTP/1.1 fetch origin
git switch chore/offline-demo-20261101
```

Run the `app` configuration on the existing Medium Phone API 35 emulator. Without backend settings, the home screen opens without signing in. No server URL is needed. Existing local wrapper edits should be preserved.

This is source delivery for Android Studio. The existing configured CI APK uses placeholder account settings and is only a build-validation artifact.

## Review and limits

A fresh read-only review found no Critical or Important issues. Missing configuration is checked before creating AuthViewModel; local records use the reserved owner `__offline_demo_v1__`, and existing configured login/ownership behavior is preserved. No new dependency, database migration, signing change or backend deployment was introduced.

These checks do not establish physical-device behavior, process-death recovery, or actual EMG hardware communication. The current flow continues to label and use mock signals. A board and sensor transport protocol must be specified for real sensor integration.
