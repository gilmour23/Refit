# ReFit phone installation and security review — 2026-11-05

Scope: existing vision/sensor Android app at 46886c9, native installable APK profile, photo HTTP client/storage, measurement server access and upload bounds, BLE permissions, local encryption and app signing. This is source review plus automated tests and APK inspection, not physical-device penetration testing or a medical accuracy assessment.

## Findings addressed

1. High: the supplied measurement server allowed unauthenticated photo uploads, session listing and report/CSV/JSON downloads. Added constant-time Bearer-key checking before request-body processing. Missing server configuration fails closed. Upload keys cannot read reports; GET/HEAD/report/list operations require an independently configured administrative key. `/health` remains public. Private responses use `Cache-Control: no-store`.
2. Medium: server reads and OpenCV decode had no independent byte/pixel ceiling. Added 42 MiB total request and 20 MiB per-image limits, JPEG/PNG validation and 24 MP image bounds before CV decode. Fixed-length and chunked HTTP bodies are tested. Original-photo file retention is now opt-in (`REFIT_VISION_KEEP_ORIGINALS=1`); generated reports remain on the server behind administrative access. New session directories use owner-only permissions on POSIX systems.
3. Delivery risk: the earlier CI artifact was a debuggable APK allowing development HTTP, built after CI injected test Supabase settings. Added a separate non-debuggable `phone` profile, HTTPS-only networking, blank cloud/test settings regardless of developer local.properties, and required measurement-server credentials. The package `com.refit.app.phone` installs alongside earlier packages without deleting their data. Android 8.0/API26 is the minimum.
4. Added encrypted, exact-destination-bound storage for measurement-server credentials using Android Keystore AES-GCM. Credentials are not in BuildConfig, not included in the APK, not saved in instance-state bundles and not placed in URLs. A changed destination cannot reuse a different destination's stored credential. Invalid header characters and insecure destinations are rejected before opening photos.
5. Signing: the distributed APK is signed after CI using an encrypted private keystore outside source control. No private signing key or password enters public CI artifacts. Key/password recovery files are kept separately, never bundled together or embedded in the APK.

## Controls checked

- Existing owner-scoped AES-GCM encryption for saved measurement/rehab records and reusable login credentials. Private reports/photos are not retained in Android registration preferences.
- Backup/device-transfer exclusion; no broad storage/camera permission; location permission only for BLE on Android 11 or earlier. Real BLE observations only; no mock connection success imported. Discovery callbacks run on the main handler, reject late scan events and recover if Bluetooth permission is revoked while reading device identity.
- Only the launcher MainActivity is intentionally exported; no WebView, custom permissive TrustManager or TLS hostname bypass found in application code.
- Source RLS policies enforce authenticated ownership, including both USING and WITH CHECK for updates. The phone build uses no cloud project; no production cloud settings or patients were queried or changed.
- Package-level verifier inspects the generated binary manifest and DEX, requiring non-debuggable, no cleartext/backup, only expected export/permissions and no CI test URL/key. Signing and final APK SHA-256 are checked separately after local signing.

## Tests and limits

Python: `PYTHONPATH=vision python -m unittest discover -s vision/tests -v` (7 tests at implementation). Actual FastAPI test application covers auth, admin/read separation, missing configuration, request-size limits and private-response caching; image guards use real Pillow. These do not establish the original vision algorithm's measurement accuracy. Original EXIF is transmitted with the authorized photo request to preserve camera calibration; the server must be treated as a trusted processor.

Android: full existing offline/configured suites plus original-byte/Authorization header tests, encrypted destination-specific credential tests, pre-upload denial and phone-profile checks; lintDebug/lintPhone and APK binary inspection. Exact CI results are linked in the PR after the run completes.

Physical installation/real BLE hardware, two real production accounts, historical key backups and a dependency-wide CVE audit are outside the evidence available in this run. A rooted/compromised device remains outside the Android sandbox threat model. BLE service UUID selection is configuration, not proof of a genuine peripheral's identity; pairing/authentication depends on the still-unconfirmed firmware. Measurement server reports remain stored until removed by its operator; no automatic retention deadline is claimed.

Official references: [Android network security](https://developer.android.com/privacy-and-security/security-config), [Android app signing](https://developer.android.com/studio/publish/app-signing). No certificate/hostname checks are disabled for local development.
