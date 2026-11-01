# Android center cloud integration

Source baseline: gilmour23/Refit chore/wrist-fitting-demo-20261101 at 781a6eb2565dd2142a19551638b1413787931f64. This preserves offline demonstration, first-use brace setup, registered brace details and explicit new-brace flow.

## Server prerequisite

The center-care additive migration must be applied before using this app's online integration. The repository's older supabase/schema.sql alone provides only summary sessions and cannot create center links, plans or signal uploads. The center web integration owns migration 20261101080949_center_patient_care.sql (SHA256 a7bb5d023457ad17cedaf1fcc2fcaa1cfdc7b41ede88960f3538cf5b1e9a6674), with owner/center RLS, explicit-consent RPCs and immutable clinician plan history. Existing rows remain unknown/no-raw. This Android branch does not change database permissions or grant staff access.

## Data contract

The authenticated owner upserts public.rehab_sessions using user_id,client_local_id. Existing summary fields remain. Nullable planned_attempts,completed_attempts,attempt_seconds,rest_seconds,ended_early,wrist_side and original comfort_response,pain_response,fatigue_response are uploaded. Categorical responses are never fabricated as numeric scores. data_mode maps demo to demo, sensor/device to device, and every unknown historical value to unknown. Only newly recorded provenance sets source_device and source_app_version.

After summary acknowledgement, a valid captured signal is upserted in public.rehab_session_signals by session_id. A session is only confirmed synced after both operations succeed. Failed or lost acknowledgements retain encrypted local capture and retry the same identifiers. Symptom edits mark rows pending; an edit concurrent with upload cannot be marked confirmed by an older acknowledgement.

Raw signal remains inside the existing encrypted JSON payload. Room schema stays at version 5; added serializable fields default to null, so existing ciphertext migrates through normal decoding without fabricating measurements.

## Capture semantics

The accumulator retains the first 20,000 finite active-exercise samples while computing summary metrics over every accepted sample. captured_samples counts those samples and truncated is true exactly when captured_samples exceeds the retained count. READY and paused intervals are excluded. Values are a concatenated active-recording sequence; wall-clock gaps are not represented as samples.

Mock data uses nominal 50 Hz from the source's 20ms emission delay, channel mock_emg and unit arbitrary; all uploads are labeled demo. BLE raw capture is disabled unless a real protocol supplies BleEmgConfig.captureMetadata with rate/channel/unit. The parser's actual output unit and nominal rate must be supplied by its integrator; the app does not guess ADC scaling, sensor rate or channel. Missing/invalid/unknown raw capture never generates synthetic data.

## Patient consent and plans

Signed-in users can enter a clinician-generated opaque invite code and check explicit sharing consent before accept_center_invite(p_code) is called. The token is held only in unsaved UI memory and is not logged. No automatic center linking or staff role changes occur. Patient revoke_center_link(p_center_id) is exposed with confirmation. The account-scoped ViewModel is cleared on logout.

Only plans for the signed-in patient, an active center_patients link (revoked_at null), wrist_extension, the current effective dates and weekday are candidates. Latest effective_from/created_at wins. App-supported limits are 1..30 attempts and 1..60 seconds for both attempt and rest. Source center name, effective dates, created date, daily goal and reason are displayed. Active linkage and plan identity are revalidated before application and each selected-plan exercise start; unavailable or revoked plans cannot start guidance. The patient explicitly applies a plan; reading a plan never changes guidance. Manual timing edits clear the selection. Plan selection is memory-only and must be explicitly applied again after app restart. Revoked/expired/unavailable plans are removed; an already running interval retains its fixed timing and the next exercise returns to manual settings.

## Verification

Tests cover metadata fidelity, legacy unknown records, bounded raw capture, partial upload failure/retry, concurrent symptom edits, consent gating, account guards, revoked linkage exclusion, date/day/owner/unsupported plan selection and explicit application. Existing offline/brace/auth/encryption tests remain in the full suite. GitHub Android CI is the build authority because this runtime has no Android SDK; the public verified Gradle wrapper downloads but Java network bootstrap is unavailable locally.

The CI first tests blank configuration, then uses isolated fake HTTP configuration for unit tests and debug lint/build, then creates a configured QA APK with the existing non-debuggable QA build. Only public project URL and publishable key are used; service credentials are never included.
