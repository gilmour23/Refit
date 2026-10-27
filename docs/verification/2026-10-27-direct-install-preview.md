# Direct-install preview verification — 2026-10-27

## Delivery and installation

ReFit 0.6.0-preview is a separate, non-debuggable Android package (`com.refit.app.preview`), labelled **Re:Fit 검토**. It does not replace `com.refit.app`. Keep the original installed to retain its local records; the preview has separate local storage and does not import or download the original records. Sign in with the existing account after installation.

The original 0.5.3 APK certificate SHA-256 is `ccc640c00bb3e199b9e5f5def344fd9e7ca7dfd815e61764777036df8aeb6ace`. Its private key was not found in the current workspace or available source archive. The preview therefore uses a dedicated key and application ID; do not attempt a same-ID update with a different signer.

## Build and package evidence

- `:app:assemblePreview :app:lintVitalPreview :app:testDebugUnitTest --tests '*MainActivityLaunchTest'`: **BUILD SUCCESSFUL**, 80 tasks (47 executed, 33 up-to-date).
- Robolectric startup/recreation test: 1 test, 0 failures, 0 errors, 0 skipped. This uses the debug test variant; actual-device preview startup/login is not yet verified.
- Prior UI implementation CI at `554a60db011096a231cfb255117c5de4b340ffb7`: 78 tests passed, full debug lint/build passed. [CI run](https://github.com/gilmour23/Refit/actions/runs/40469698709).
- Final preview APK: 13,397,705 bytes; SHA-256 `ab90827e95d5ddde1665b404641f200af9add2426a64f0c9e6994d1c778af580`.
- APK signature v2 verifies. Dedicated signer SHA-256: `b556afc2d65b048da0eb502a8dc6a22a8754cc022bd987b04b208b4ce8a8e3cc`; matches the private signing backup and differs from the original app signer.
- Package/launcher/label checked with aapt; ZIP integrity passed. Debuggable false, backup/full backup disabled, cleartext traffic disabled; no debug preview activities.
- APK contains the actual project URL and existing enabled publishable client key. No signing file, local.properties, or server secret key is bundled.
- Private signing material and password are backed up separately from source/APK. They must remain private and be retained for future preview updates.

## Backend evidence

The existing ReFit Supabase project was inactive and was restored to **ACTIVE_HEALTHY**. Auth health and settings endpoints returned HTTP 200 with the existing publishable key. Anonymous access to profiles and rehab_sessions returned HTTP 401; RLS is enabled on both tables and authenticated ownership policies are present. No patient data was queried or changed.

The security advisor still reports **Leaked Password Protection Disabled**. It was not enabled or upgraded as part of restoring service. [Remediation guidance](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

## Rebuilding the preview

Use Java 17 and the project Gradle wrapper. Supply ignored local.properties values `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `REFIT_PREVIEW_STORE_FILE` (absolute keystore path) and `REFIT_PREVIEW_STORE_PASSWORD`. The dedicated key alias is `refit-preview`. Do not commit credentials or key material. Build with `./gradlew :app:assemblePreview :app:lintVitalPreview`.

## Remaining device verification

Actual phone installation and user authentication are pending. Physical EMG/BLE and orthosis motor integration are not validated by this build; current demo signals and activation display must not be interpreted as measured recovery. This preview is for reviewing the implemented patient UI and existing app flow.
