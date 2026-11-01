# Center cloud integration implementation plan

Goal: extend the existing signed-in Android app to upload its full recorded session metadata, bounded raw signals, and explicitly apply clinician plans after patient consent to center linking.

Base: gilmour23/Refit chore/wrist-fitting-demo-20261101 at 781a6eb2565dd2142a19551638b1413787931f64.

1. Add tests for original symptom response preservation, unknown-source/no-raw behavior, bounded capture, raw upload retry, symptom edits during sync, plan owner/date/day selection and invite consent.
2. Extend encrypted session JSON with optional raw capture and provenance, preserving existing records without migrations or fabricated samples.
3. Upload nullable guidance/response metadata; upsert raw only after summary acknowledgement; mark synced only when both succeed. Keep newer symptom edits pending.
4. Read own active wrist_extension plans through authenticated RLS. Show source/date/reason and apply by explicit action within existing timing bounds; manual edits remove selected source.
5. Add signed-in center connection screen with explicit consent and invite RPC; no automatic linking or staff grants. Offline demo UI remains account-free.
6. Run feature tests, full unit suite, lint and debug build in existing GitHub CI; review branch only, no merge. Local execution requires Android SDK and binary wrapper unavailable in this tool snapshot.
