# QR photo intake and operator results

User-directed revision: participant QR opens filming instructions, participant identity and top/side photo upload. Operator selects participants from a durable server list to see their annotated measurement report and eleven values. Galaxy and iPhone can submit photos. Existing laptop HTTPS hosting and APK endpoints remain.

Use the existing FastAPI server plus local SQLite/file storage. A separate public upload page and operator shell share one HTTPS origin. A separate hosted database would add setup without helping the requested laptop demonstration. Identity defaults to display name/nickname and left/right hand; generated random record IDs distinguish duplicate names. No contact/medical fields are added.

Uploads explicitly disclose and require consent to store original photographs, nickname, hand, report and measurements on the operator laptop until the operator deletes them. Intake originals are intentionally stored, unlike legacy API defaults. No participant can list/read others' records. QR carries only the upload key; operator key stays in a separate local-only launch page. Operators authenticate through a password field, keys remain memory-only, never URL parameters or local storage.

A bounded queue processes one measurement at a time, shares the existing measurement lock, records queued/processing/complete/failed states, retains failure messages and original photos, and does not invent missing measurements. SQLite survives server restarts; interrupted jobs become failed and can be resubmitted. Per-record receipt tokens permit polling only a submission's state. Admin can list/search records, select details, retrieve allowlisted images/CSV and delete finished/failed records plus files. Processing/queued records cannot be deleted until finished.

Presentation: split annotated photos and color numbered dimensions, top-photo lengths/widths and side-photo thicknesses, explicit mm, downloadable combined report/CSV. Keep all existing dimension definitions: user chose existing calculation for ⑨ (5→ulnar edge), not the reference image's 17↔5 distance.

Privacy boundaries: exact public page/asset allowlist; /admin/* requires admin key for every method; uploads and receipt polling require upload key. No keys in static bundles or image URLs. Images are fetched with authorization and displayed as temporary blob URLs. Original uploads retain bytes/EXIF; normalized previews contain no EXIF. Bounded uploads/queue/storage and validated IDs prevent accidental runaway use/path traversal.

Unverified until real rehearsal: mobile camera/browser behavior, public tunnel, valid photo measurement accuracy and actual BLE. Existing BLE demo remains accessible on its separate /demo page.
