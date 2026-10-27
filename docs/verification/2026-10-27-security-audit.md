# Security audit — 2026-10-27 (KST)

## Scope and outcome

Read-only review of the delivered ReFit 0.6.0-preview APK, current tracked application source, Supabase schema/policies/advisors and unauthenticated API responses. No app/auth configuration or patient data was changed. No critical credential exposure or open record access was identified within these checks; this is not a full penetration test or a release security certification.

## Confirmed findings

### S1 — Medium: persisted login session lacks application-level encryption

SupabaseClientProvider installs Auth with automatic storage but no custom sessionManager. Inspected resolved auth-kt 3.8.0 Android bytecode: SettingsUtilKt.createDefaultSessionManager uses SettingsSessionManager; its saveSession serializes UserSession as JSON through SuspendSettings.putString. The resolved multiplatform-settings-no-arg 1.3.0 NoArgKt.Settings uses Context.getSharedPreferences(packageName + "_preferences", MODE_PRIVATE). Therefore reusable session credentials are stored without application-level encryption. This is private internal storage, not publicly readable storage; the threat requires app-private file access (for example a compromised/rooted device). Android's own filesystem encryption and sandbox remain separate protections.

Remediation: implement a SessionManager with authenticated encryption using a key held by Android Keystore; migrate the existing session once and remove plaintext copies. Preserve offline logout deletion and account/race behavior. Never persist the user's password.

### S2 — Medium: local rehabilitation database lacks application-level encryption

ReFitDatabase builds ordinary Room SQLite refit.db without a database encryption layer. It contains user identifiers, rehabilitation summaries and locally entered pain/fatigue. Backup and device transfer are excluded. Other ordinary apps cannot directly read this sandbox. The remaining risk is extraction of app-private storage on a compromised device.

Remediation: choose an encrypted database or encrypt the sensitive fields with managed device keys, including a non-destructive migration and recovery/deletion behavior. Do not erase existing records as a shortcut.

### S3 — Medium: leaked-password rejection is disabled

Fresh Supabase security advisor returned one WARN: auth_leaked_password_protection. The service currently does not reject known leaked passwords through this feature. This is a hardening gap, not evidence of a compromised account.

[Supabase remediation](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). Official docs state this feature requires Pro or above; no paid-plan change was made.

AuthViewModel also accepts passwords of six or more characters. This establishes only the client-side minimum; the server-side minimum/complexity configuration was not available through the connected tool surface and was not independently established. Strengthen new-password validation together with server policy without blocking existing users from signing in with legacy credentials.

### S4 — Medium operational exposure: signing backup co-locates key and password

The separately stored preview signing ZIP is not ZIP-encrypted and contains both the PKCS12 key and its password in signing.json/README. It is not in the APK or tracked source and was saved to the user's private file account. Disclosure of that archive would disclose the signing capability. No disclosure was demonstrated.

Remediation: encrypt the backup with a separately retained secret or use a dedicated secret manager; keep key and password access separately controlled. Retain a recoverable backup to avoid another signing-key loss. No key rotation or deletion was performed.

## Verified controls

- Rechecked the delivered APK: SHA-256 ab90827e95d5ddde1665b404641f200af9add2426a64f0c9e6994d1c778af580. Signature v2 verifies; package com.refit.app.preview; non-debuggable; backup and cleartext traffic disabled.
- APK excludes signing keys and local.properties. Scans found no sb_secret key or service_role JWT. Current git-tracked source scans found no private-key headers, Supabase secret keys, GitHub token patterns or tracked signing/local configuration files. This does not cover all historical commits.
- Embedded publishable client key is expected for a mobile app; protection depends on authentication and RLS. [Official API-key guidance](https://supabase.com/docs/guides/getting-started/api-keys).
- Live profiles and rehab_sessions both have RLS enabled. All eight policies target authenticated users and enforce auth.uid() ownership; UPDATE has both USING and WITH CHECK. No public-schema views/functions or Storage buckets exist in the metadata inspected.
- Requests using only the publishable key returned HTTP 401 for both tables. Auth settings returned HTTP 200, anonymous-user login false and mailer_autoconfirm false (email confirmation required).
- Local DAO reads/updates and synchronization are scoped by owner. Logout clears reusable local credentials before remote revocation and disposes account-owned UI state.
- Fresh command: :app:testDebugUnitTest --tests '*Auth*' --tests '*Account*' --tests '*Sync*' — BUILD SUCCESSFUL, 32 tests, 0 failures/errors/skipped. Includes offline/delayed/cancelled logout, account change and unauthorized synchronization cases. These use local/mock services, not two real production accounts.
- No application logging of passwords/tokens was found in the inspected main source.

## Remaining boundaries

No actual-phone forensic test, two-real-account authorization test, dependency-wide CVE scan, auth rate-limit/CAPTCHA configuration review or historical secret scan was completed. BLE implementation selects devices by service UUID/name without explicit trusted-device authentication or enforced encrypted pairing; it is not validated with a physical sensor, and current demo-mode operation does not exercise that path. Before integrating real EMG/motor hardware, specify device authentication, replay handling and fail-safe motor behavior.

Recommended order: protect persisted session credentials; protect local patient records with migration; strengthen auth policy; secure signing backup handling. Patient-facing deployment remains pending these decisions and physical-device verification.
