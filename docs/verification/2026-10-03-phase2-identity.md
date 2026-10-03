# Phase 2: identity and user-owned local records

Date: 2026-10-03
Baseline: remote `f417e3e061c6c666f7c8d00fae906bbe065fcd1a`
Scope: second delivery, login restoration + user-owned Room persistence. Main is not merged.

## Changes

- Explicitly enable Supabase Auth session saving, loading and automatic refresh.
- Retryable refresh failures retain the persisted owner for offline local exercise/history. The home screen displays the pending connection status. SDK `NotAuthenticated` never uses this fallback.
- Log out of the current device (`LOCAL`). Always delete local SDK credentials, including when the logout HTTP request fails or is cancelled. Offline logout does not claim that the server has revoked its tokens.
- Keep account ViewModels in a store owned by the activity-retained AuthViewModel. Clear that store on logout, a different owner, or final AuthViewModel disposal. Keep the store on same-owner refresh and transient SDK Loading.
- Disconnect acquisition and clear in-memory exercise/result/history state when its ViewModel is cleared.
- Disable Android backup for the MVP so app credentials and local rehabilitation records are not included in standard app backups.
- Add real Room migration, reopen and owner-isolation tests, and real Supabase SDK persistence tests backed by Android SharedPreferences.
- Upload test reports and debug APK in Android CI; limit new CI jobs to 15 minutes and cancel superseded runs within their concurrency group.

## Verification

Command: `gradle :app:testDebugUnitTest :app:assembleDebug`

Fresh local result: **BUILD SUCCESSFUL**, 17 tests, 0 failures, 0 errors; debug APK generated.
Local-only SDK/JDK/network configuration was supplied outside the repository; no server credentials were supplied.

| Suite | Tests | Checked behavior |
|---|---:|---|
| AuthRepositoryTest | 6 | Password login through a controlled HTTP response; persist tokens without the password; client recreation restores owner; offline logout clears memory/storage; next owner restoration; retryable refresh preserves local owner; cleared session requires login |
| AuthAccountScopeTest | 1 | Transient Loading and same-owner refresh preserve the exercise scope; logout and owner change dispose it; final root disposal clears children |
| RehabSessionDaoTest | 3 | History/latest/pending rows filtered by owner; records survive DB reopen; v1 migration preserves unowned legacy history without assigning it to a new account |
| SessionLogoutTest | 1 | Account-scope disposal disconnects acquisition and removes active/result state |
| Existing EMG and packet tests | 6 | Existing metric and parser regression suite |

Before the production fixes, 16 tests ran with exactly three assertion failures: refresh failure lost the owner, offline logout retained credentials, and clearing the account scope retained acquisition. After the fixes, the full suite passed. A fresh read-only code review found no material issues within this delivery.

The SDK test fixture enables platform initialization and disables lifecycle callbacks. Disabling platform setup entirely prevents empty-session initialization from completing; the initial CI test commit used that incorrect fixture and is superseded by this corrected version.

## Pending acceptance

- There is currently no connected Supabase project. Real server sign-up/login, email confirmation, refresh/revocation and server configuration have not been exercised.
- Install a configured APK on Android, log in, restart the process, and verify account switching on that device. Rotation/background behavior is supported by scope tests and code review; it has not been exercised on a physical device in this delivery.
- Cloud synchronization and actual BLE/EMG sensor behavior belong to the later agreed deliveries.

Account-scope decision: preserving the scope across rotation/transient Loading prevents an active exercise from being discarded. If the SDK subsequently reports a cleared/revoked session, the account scope is disposed immediately. Creating a cloud project is deferred because organization/cost selection is still missing.
