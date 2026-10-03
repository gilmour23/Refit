# ReFit hosted Supabase deployment

Date: 2026-10-03

## Target and deployment

- Project: ReFit (`yqudfmmdderlbrzrfply`), organization `bluesjyh04` (`bdpsmbqtutnuhkzajtin`).
- Region: Seoul (`ap-northeast-2`); project status `ACTIVE_HEALTHY`.
- API URL: `https://yqudfmmdderlbrzrfply.supabase.co`.
- Hosted Postgres: 17.11.0.002.
- Initial public schema was empty. Applied `refit_v1_initial_schema` and `refit_v1_limit_client_table_privileges` migrations.
- `profiles` and `rehab_sessions` have RLS and four owner policies each. Updates check both existing ownership and resulting ownership.
- Client grants: authenticated SELECT/INSERT/UPDATE/DELETE only; anon has none. Server-side service-role privileges are unchanged.
- Android configuration uses the enabled `sb_publishable_` key in ignored `local.properties`; no service-role key, database password, or sign-in credential was fetched or embedded.

## Verified

- Reproduced a hosted default-grant issue: both client roles inherited ALL, including TRUNCATE, which RLS does not restrict. A privilege assertion failed before revocation and passed afterward.
- Ran `supabase/tests/ownership.sql` against the hosted project, omitting only the psql client directive. Used the real hosted `auth.uid()`; never ran the disposable test bootstrap on Supabase.
- Transaction passed owner insert/read, composite retry preserving row ID, cross-owner read/update/delete/insert denial, owner reassignment denial, profile isolation, anonymous RLS denial, and client table privilege assertions.
- All synthetic Auth users, profiles, records, and temporary test grants rolled back. Follow-up counts: 0 Auth users, 0 profiles, 0 rehab sessions.
- Auth settings endpoint: HTTP 200; email authentication enabled and email confirmation required.
- Direct REST requests using the publishable key without a user session: both tables reject reads with HTTP 401 / Postgres `42501` (permission denied).
- Security and performance advisors: no lints.
- Configured local Android build: 33 tests, 0 failures/errors/skips; debug APK succeeds. The acquisition/logout test now explicitly uses the real coordinator in offline mode rather than accidentally initializing a live SDK when build settings are nonblank. SDK/Room sync integration tests remain intact.
- Merged Android manifest includes Supabase, settings, and process-lifecycle startup initializers.
- CI exercises unit tests with nonblank placeholder settings, then removes those settings before assembling its unconfigured generic APK. The connected local APK is a separate build.

## Remaining acceptance

Hosted transaction tests simulate authenticated claims; they do not issue or validate real Auth JWTs. No actual user account, email confirmation, phone/emulator login, authenticated REST upload from Android, or physical sensor acquisition was performed.

On an Android device, install the connected debug APK, create/confirm an app account, log in, complete a mock exercise, and verify one owned cloud row. Then check offline save/reconnection/retry, process restart, logout during upload, and separation of two real accounts. Real BLE still needs the sensor's service/characteristic UUIDs and packet specification.

The Postgres 17.11 breaking-change notice affects legacy pgcrypto PGP ciphers and certain existing indexes/operators; this fresh schema uses none of those affected features.
