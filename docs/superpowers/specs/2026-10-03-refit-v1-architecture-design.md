# Re:Fit V1.0 Architecture Design

Date: 2026-10-03
Status: Foundation implemented; Phase 2 automated identity/local-storage verification complete; live Supabase acceptance pending
Repository: gilmour23/Refit

## 1. Goal

Re:Fit V1.0 should operate as a real Android rehabilitation MVP, not only as a UI prototype.

The app must support:
- user sign-up and login
- persistent authenticated session across app restarts
- user-specific rehabilitation history
- BLE-based sEMG acquisition
- local real-time sEMG processing
- session-level feature calculation
- offline-first local persistence
- later synchronization of summarized session results to Supabase
- history/progress views based on the authenticated user

The current V0.3 implementation is retained and extended rather than rewritten.

## 2. Current Baseline

Current implementation already includes:
- Kotlin + Jetpack Compose
- MockEmgDataSource behind EmgDataSource
- SessionViewModel
- real-time graph of recent samples
- session sample accumulation
- RMS and MAV calculation
- maximum activation calculation
- Room-based RehabSession storage
- result and history screens

Current gaps:
- no authentication
- no user identity attached to stored sessions
- no persistent cloud account/session
- no BLE sEMG source
- no real signal preprocessing
- no offline/cloud sync state
- raw signal metrics are still MVP-oriented

## 3. Architecture

High-level flow:

```text
Supabase Auth
     |
 authenticated userId
     |
     v
Android App
  |
  +-- AuthRepository
  |
  +-- EmgDataSource
  |     +-- MockEmgDataSource
  |     +-- BleEmgDataSource
  |
  +-- EmgSignalProcessor
  |     +-- preprocessing
  |     +-- realtime metrics
  |     +-- session metrics
  |
  +-- RehabSessionRepository
        +-- Room
        +-- Supabase sync
```

The design remains Android-native and keeps Jetpack Compose.

## 4. Authentication Design

Supabase Auth will be the single account/authentication source.

### Required behavior

First launch:
```text
App start
-> load auth state
-> no session
-> Login / Sign-up
-> authenticated
-> Home
```

Later launches:
```text
App start
-> restore persisted Supabase session
-> refresh session if required
-> authenticated
-> Home
```

A password must never be stored directly in Room, DataStore, SharedPreferences, or application files.

### Auth states

The app should model authentication explicitly:
- Loading
- Authenticated(userId)
- Unauthenticated
- Error

The root navigation graph should be selected from auth state, rather than having individual screens decide whether the user is logged in.

## 5. User Profile

Authentication identity and application profile are separate.

Supabase table:

```text
profiles
- id UUID PK/FK -> auth.users.id
- display_name
- birth_year nullable
- affected_side nullable
- created_at
- updated_at
```

Only information necessary for the MVP should be collected.

No diagnosis or additional health information should be added unless it is required by a defined feature.

## 6. Local Session Data Model

The current RehabSessionEntity should be extended.

Recommended logical fields:

```text
rehab_sessions
- localId
- remoteId nullable
- userId
- createdAt
- exerciseType
- durationSeconds
- sampleCount
- rms
- mav
- peakActivation
- activationDuration nullable
- syncStatus
- updatedAt
```

Recommended syncStatus values:
- LOCAL_ONLY
- SYNCING
- SYNCED
- FAILED

Every session must belong to a userId.

History queries must always filter by the currently authenticated user.

## 7. EMG Acquisition

EmgDataSource remains the abstraction boundary.

```text
EmgDataSource
  |
  +-- MockEmgDataSource
  |
  +-- BleEmgDataSource
```

SessionViewModel must not depend directly on BLE APIs.

BleEmgDataSource will be responsible for:
- BLE scan
- device selection/connection
- GATT discovery
- characteristic subscription
- packet parsing
- conversion from sensor packets to EmgSample
- connection state reporting
- disconnect/reconnect errors

Mock and BLE implementations should emit the same EmgSample model.

## 8. EMG Signal Processing

Signal processing occurs locally on the Android device.

The backend should not be required for real-time feedback.

### Processing stages

Target pipeline:

```text
Raw sensor packets
-> unit conversion/calibration if needed
-> DC offset removal
-> band-pass filtering
-> mains-frequency noise suppression if required
-> rectified/windowed representation
-> real-time metrics
-> session metrics
```

Exact filter frequencies MUST NOT be hard-coded until the actual sensor sampling rate, analog front-end behavior, and sensor output format are confirmed.

### Realtime metrics

Realtime UI should operate on a rolling window rather than the complete session.

Initial target:
- rolling RMS
- current activation indicator
- visible waveform

Window size should be configurable after the actual sensor sampling frequency is known.

### Session metrics

At session completion:
- RMS
- MAV
- peak activation
- duration
- sample count

Potential later metrics:
- activation duration
- integrated EMG
- contraction count
- baseline-normalized activation

These later metrics are out of scope until the measurement protocol is defined.

### Peak correction

The current `maxOrNull()` implementation is not adequate as a generic raw EMG peak measure because raw sEMG is bipolar.

Peak activation must be calculated from the intended processed signal definition, for example absolute/rectified or filtered amplitude.

## 9. Raw EMG Storage Policy

The cloud database must not store one database row per raw sample.

For V1.0:
- realtime raw samples remain in memory while a session is running
- summarized metrics are stored in Room
- summarized metrics are synced to Supabase

Optional later design:
- raw session signal serialized as CSV/binary
- stored as a file in local storage and/or Supabase Storage
- session table stores only metadata and storage path

Raw-signal persistence is not required for the first working MVP unless needed for model development or validation.

## 10. Offline-First Persistence

Room remains the primary local source for completed session results.

Flow:

```text
Session ends
-> calculate metrics
-> save to Room immediately
-> show result immediately
-> attempt cloud sync
-> update syncStatus
```

The app must remain usable when the network is temporarily unavailable.

A failed upload must not lose the local session.

## 11. Supabase Cloud Data Model

Initial cloud tables:

### profiles

```text
id UUID primary key
display_name
birth_year
affected_side
created_at
updated_at
```

### rehab_sessions

```text
id UUID primary key
user_id UUID
created_at
exercise_type
duration_seconds
sample_count
rms
mav
peak_activation
activation_duration nullable
client_local_id
updated_at
```

A client-side local identifier should be sent so retries can be made idempotent and duplicate sessions can be avoided.

## 12. Access Control

Every exposed Supabase table must use Row Level Security.

A user may only read/write rows owned by that user's authenticated UUID.

Conceptually:

```text
profiles.id == auth.uid()
rehab_sessions.user_id == auth.uid()
```

The Android client must only contain a public/publishable key.

The Supabase service-role key must never be included in the application.

## 13. Repository Boundaries

Recommended package responsibilities:

```text
auth/
  AuthRepository
  AuthState

data/local/
  ReFitDatabase
  RehabSessionDao
  RehabSessionEntity

data/remote/
  SupabaseClientProvider
  RehabSessionRemoteDataSource

data/repository/
  RehabSessionRepository
  ProfileRepository

data/source/
  EmgDataSource
  MockEmgDataSource
  BleEmgDataSource

signal/
  EmgSignalProcessor
  RealtimeEmgWindow

ui/auth/
ui/home/
ui/session/
ui/result/
ui/history/
ui/profile/
```

The current code can be migrated toward this organization incrementally.

## 14. UI State Flow

Root flow:

```text
Splash/Auth loading
   |
   +-- unauthenticated -> Login / Sign-up
   |
   +-- authenticated -> Home
                         |
                         +-- Sensor
                         +-- Exercise Session
                         +-- Result
                         +-- History / Progress
                         +-- Profile
```

Exercise flow:

```text
Sensor disconnected
-> scanning
-> connecting
-> connected
-> ready
-> countdown
-> exercising
-> saving
-> result
```

Connection and save states must be visible in the UI.

## 15. Sync Strategy

V1.0 does not require complex realtime cloud synchronization.

Use a simple eventual-sync policy:
1. save locally
2. upload after session completion
3. retry failed LOCAL_ONLY/FAILED sessions on a later suitable opportunity
4. mark as SYNCED after a confirmed remote write

Do not stream each EMG sample to Supabase.

## 16. Error Handling

Minimum required cases:
- login failure
- expired/restored auth session failure
- network unavailable
- BLE permission denied
- BLE scan failure
- BLE connection loss
- malformed sensor packet
- no EMG samples
- Room write failure
- Supabase sync failure

A BLE/network failure must not crash the app.

Cloud sync failure must not invalidate an already saved local session.

## 17. Testing

### Unit tests
- RMS
- MAV
- peak metric definition
- rolling RMS
- packet conversion when BLE format is available
- session-to-entity mapping
- sync state transitions

### Repository tests
- insert session for current user
- history filtered by user
- failed sync remains local
- successful sync becomes SYNCED

### UI/ViewModel tests
- auth state routing
- start/stop session
- result generation
- BLE disconnect state
- retryable save/sync state

### Manual device test
- install app on physical Android device
- login
- close/reopen app and confirm session restoration
- connect BLE sensor
- acquire signal
- complete exercise
- verify Room persistence
- disable network and repeat
- re-enable network and verify cloud sync

## 18. Implementation Order

### Phase 1 — Identity foundation
- Supabase project
- Supabase Auth
- AuthRepository
- Login/Sign-up
- persisted session restoration
- profiles table + RLS

### Phase 2 — User-owned local data
- add userId to Room sessions
- migrate Room schema
- filter history by active user
- add sync state

### Phase 3 — BLE acquisition
- runtime permissions
- scanning
- connection
- characteristic subscription
- BleEmgDataSource
- Mock/BLE switch

### Phase 4 — Signal processing
- confirm sensor sample rate and packet format
- preprocessing
- rolling RMS
- corrected peak metric
- session feature calculation

### Phase 5 — Cloud synchronization
- rehab_sessions table + RLS
- Room -> Supabase upload
- retry/idempotency
- sync status UI where useful

### Phase 6 — Product UI polish
- Figma design system
- Home
- sensor connection states
- exercise countdown
- realtime activation feedback
- result/history/progress screens

## 19. Non-Goals for V1.0

Not required for the first working MVP:
- clinician portal
- multi-role hospital administration
- AI-generated medical diagnosis
- raw EMG row-by-row cloud streaming
- advanced ML-based rehabilitation recommendation
- iOS version
- background continuous EMG recording
- full medical-device production compliance implementation

## 20. Definition of Done

Re:Fit V1.0 is considered functionally complete when a user can:

1. create an account and log in
2. close and reopen the app without unnecessary re-login
3. see only their own rehabilitation history
4. connect to the selected sEMG BLE sensor
5. start an exercise and see live EMG feedback
6. finish the exercise and receive calculated session metrics
7. have the session saved locally even without internet
8. later synchronize the session result to their cloud account
9. reopen the app and retrieve their saved rehabilitation history


