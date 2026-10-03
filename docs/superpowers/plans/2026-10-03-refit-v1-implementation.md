# Re:Fit V1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Re:Fit V0.3 into an Android MVP with persistent user authentication, user-owned rehabilitation sessions, BLE-ready EMG acquisition, stronger EMG metrics, offline-first persistence, and Supabase synchronization boundaries.

**Architecture:** Keep the existing Kotlin/Jetpack Compose + Room codebase. Add authentication and cloud boundaries without coupling them to `SessionViewModel`; keep `EmgDataSource` as the sensor boundary and `EmgSignalProcessor` as the local signal-processing boundary. Room remains the immediate source of truth for completed sessions; cloud upload is eventual.

**Tech Stack:** Kotlin, Jetpack Compose, Android Lifecycle/ViewModel, Room, Kotlin Coroutines/Flow, Supabase Kotlin client, Android BLE APIs.

**Spec:** `docs/superpowers/specs/2026-10-03-refit-v1-architecture-design.md`

## Global Constraints

- Android native; retain Jetpack Compose.
- Minimum SDK remains 26 unless a concrete dependency forces a change.
- Passwords must never be stored in app persistence.
- Every completed rehab session must belong to an authenticated user ID.
- Raw EMG samples are not uploaded row-by-row to Supabase.
- Room save occurs before cloud synchronization.
- Exact EMG filter frequencies remain configurable until sensor sampling rate/output format are confirmed.
- Supabase service-role/secret keys must never be embedded in the Android client.
- BLE implementation must remain behind `EmgDataSource`.

## Review Focus

1. App restart with a previously authenticated user: root routing must restore authenticated state rather than showing login unnecessarily.
2. Two different users using one device: history queries must not leak the other user's sessions.
3. Network loss after an exercise: local session must remain stored and marked for later sync.
4. Empty/bipolar EMG input: metrics must return safe values and peak calculation must use magnitude rather than signed maximum.
5. BLE disconnect/malformed packet: acquisition state must fail safely without crashing or losing an already-completed local session.

---

### Task 1: Test foundation and EMG metric correction

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/refit/app/signal/EmgSignalProcessor.kt`
- Create: `app/src/test/java/com/refit/app/signal/EmgSignalProcessorTest.kt`

**Interfaces:**
- Produces: `EmgSignalProcessor.rms(samples: List<Float>): Float`
- Produces: `EmgSignalProcessor.mav(samples: List<Float>): Float`
- Produces: `EmgSignalProcessor.peakMagnitude(samples: List<Float>): Float`
- Produces: `EmgSignalProcessor.rollingRms(samples: List<Float>, windowSize: Int): Float`

- [ ] Add JUnit test dependency.
- [ ] Add tests for empty input, bipolar peak magnitude, RMS/MAV, and rolling-window RMS.
- [ ] Implement `peakMagnitude` and `rollingRms`.
- [ ] Update session result generation to stop using signed `maxOrNull()`.
- [ ] Run unit tests.

### Task 2: User-owned Room sessions and sync state

**Files:**
- Modify: `app/src/main/java/com/refit/app/data/local/RehabSessionEntity.kt`
- Modify: `app/src/main/java/com/refit/app/data/local/RehabSessionDao.kt`
- Modify: `app/src/main/java/com/refit/app/data/local/ReFitDatabase.kt`
- Modify: `app/src/main/java/com/refit/app/data/repository/RehabSessionRepository.kt`
- Create: `app/src/main/java/com/refit/app/data/model/SyncStatus.kt`
- Create/modify tests under `app/src/test/java/com/refit/app/data/`

**Interfaces:**
- Produces: user-scoped `observeAll(userId: String)`
- Produces: user-scoped `getLatest(userId: String)`
- Produces: `getPendingSync(userId: String)`
- Produces: `updateSyncState(localId: Long, status: SyncStatus, remoteId: String?)`

- [x] Extend entity with `userId`, `remoteId`, `syncStatus`, `updatedAt`.
- [x] Add Room type conversion for `SyncStatus`.
- [x] Add a schema migration rather than destructive migration.
- [x] Replace global history/latest queries with user-scoped queries.
- [x] Add pending-sync and update-sync DAO operations.
- [x] Update repository APIs and dependent code.
- [x] Verify one user's history cannot return another user's sessions.

### Task 3: Authentication boundary and persistent session routing

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/refit/app/auth/AuthState.kt`
- Create: `app/src/main/java/com/refit/app/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/refit/app/data/remote/SupabaseClientProvider.kt`
- Create: `app/src/main/java/com/refit/app/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/refit/app/ui/auth/LoginScreen.kt`
- Create: `app/src/main/java/com/refit/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/refit/app/MainActivity.kt`

**Interfaces:**
- Produces: `StateFlow<AuthState>`
- Produces: `signIn(email: String, password: String)`
- Produces: `signUp(email: String, password: String)`
- Produces: `signOut()`
- Produces: authenticated `userId` for session ownership.

- [x] Add pinned Supabase Kotlin dependencies required for Auth/Postgrest and Android-compatible Ktor.
- [x] Read Supabase URL/publishable key from BuildConfig/local Gradle properties; do not commit secrets.
- [x] Implement loading/authenticated/unauthenticated/error auth states.
- [x] Restore persisted session on app start using the Supabase client.
- [x] Add login/sign-up UI and root auth routing.
- [x] Ensure logout clears active UI user context.

### Task 4: Decouple session logic from concrete user and add realtime metrics

**Files:**
- Modify: `app/src/main/java/com/refit/app/ui/session/SessionViewModel.kt`
- Modify: `app/src/main/java/com/refit/app/ui/session/SessionScreen.kt`
- Modify: `app/src/main/java/com/refit/app/data/model/RehabSessionResult.kt`
- Create: `app/src/main/java/com/refit/app/signal/RealtimeEmgWindow.kt`

**Interfaces:**
- Consumes: authenticated `userId`
- Consumes: user-scoped repository APIs
- Produces: rolling RMS/current activation and user-owned saved session.

- [ ] Pass active user ID into session state/creation boundary.
- [ ] Add rolling RMS calculated from recent samples.
- [ ] Save corrected peak magnitude.
- [ ] Save `LOCAL_ONLY` sync state first.
- [ ] Keep completed result display independent from cloud success.
- [ ] Test empty/no-sample stop behavior.

### Task 5: BLE acquisition boundary

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/refit/app/data/source/BleConnectionState.kt`
- Create: `app/src/main/java/com/refit/app/data/source/BleEmgDataSource.kt`
- Create: `app/src/main/java/com/refit/app/data/source/EmgPacketParser.kt`
- Modify: `app/src/main/java/com/refit/app/data/source/EmgDataSource.kt`

**Interfaces:**
- Produces: same `Flow<EmgSample>` contract as mock source.
- Produces: connection state Flow.
- Requires later configuration: target service UUID, characteristic UUID, packet layout, sampling rate/scaling.

- [ ] Add Android 12+ BLE permissions while retaining compatibility behavior.
- [ ] Define connection-state model.
- [ ] Define packet parser with explicit configuration placeholder rather than guessed packet format.
- [ ] Implement scan/connect/subscribe lifecycle behind `BleEmgDataSource`.
- [ ] Fail with a clear unsupported/unconfigured state until UUID and packet layout are supplied.
- [ ] Keep Mock source available for development/demo fallback.

### Task 6: Supabase session sync boundary

**Files:**
- Create: `app/src/main/java/com/refit/app/data/remote/RehabSessionRemoteDataSource.kt`
- Modify: `app/src/main/java/com/refit/app/data/repository/RehabSessionRepository.kt`
- Create: `app/src/main/java/com/refit/app/data/repository/SessionSyncCoordinator.kt`
- Create: `supabase/schema.sql`

**Interfaces:**
- Consumes: pending Room sessions for authenticated user.
- Produces: idempotent summarized cloud session rows.
- Produces: local `SYNCED` or `FAILED` state transition.

- [ ] Define `profiles` and `rehab_sessions` SQL with RLS policies scoped to `auth.uid()`.
- [ ] Add a unique client-side local identifier for idempotent retry.
- [ ] Implement remote insert/upsert of summary metrics only.
- [ ] Implement coordinator: local save -> upload attempt -> update sync state.
- [ ] Treat network/cloud errors as retryable without deleting local data.
- [ ] Add repository tests for successful and failed sync transitions.

### Task 7: Product navigation and progress surface

**Files:**
- Create/modify UI under `ui/home/`, `ui/history/`, `ui/result/`, `ui/profile/`
- Modify: `app/src/main/java/com/refit/app/MainActivity.kt`

**Interfaces:**
- Consumes: authenticated user state, user-scoped session history, sensor/session state.
- Produces: usable end-to-end MVP flow.

- [ ] Add Home navigation entry points for exercise/history/profile.
- [ ] Make connection/saving/sync states visible where useful.
- [ ] Preserve existing live graph and result metrics.
- [ ] Add progress/history summaries from the active user's sessions.
- [ ] Verify logout/login as another user changes visible history.
- [ ] Verify offline exercise -> result -> later sync path manually.


## Delivery checkpoints

2026-10-03, second delivery: Task 2/3 automated verification completed (17 tests, debug APK build). Supabase project provisioning and live server/device acceptance remain pending. See `docs/verification/2026-10-03-phase2-identity.md`. Later tasks retain their existing implementation and require their own agreed validation.
