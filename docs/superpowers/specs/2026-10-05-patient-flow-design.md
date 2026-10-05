# Patient-guided wrist attempt flow

Approved direction: the patient's wrist may not visibly move. The app guides an attempt, makes device status understandable, supports rest/end, and records participation and symptoms without claiming recovery.

- Remember left/right wrist per account, require a choice in preparation, show it on home and records.
- Preparation shows one action at a time: side, supported arm, brace/electrode check. No invented electrode placement or fabricated product photo: placement is the location taught at initial fitting.
- Start connects, then provides a three-second READY cue before timed attempts. READY and paused time/samples are excluded from exercise metrics.
- Rest can be held using More rest; resume continues the remaining rest without increasing counts or duration during the hold.
- Replace autoscaled waveform in the patient's exercise view with a direction illustration and explicit mock/receiving/waiting/interrupted status. No activation threshold, success percentage, or recovery score.
- Observe sensor disconnection/error and stale finite samples. Pause on interruption; resuming requires connection and a fresh sample. Demo stays visibly labelled; the APK still uses the existing mock source.
- Voice preference survives account-local restarts; report unsupported Korean TTS, provide Repeat instruction, and persist the preference. Voice support must not block visual guidance.
- Keep exercise actions visible at large text sizes; compact fixed header and side-by-side large footer actions. Other pages may scroll.
- Add optional pain and fatigue feedback, preserving legacy comfort. Store side/pain/fatigue in Room v4 with a non-destructive v3 migration; show them in detail and history. Existing cloud DTO remains unchanged; these fields remain device-local.
- Guide settings remain editable demo timings. Do not present them as a clinician prescription or increase dose automatically.

Validation: real Compose action paths and screenshots; READY/rest/interruption timer tests; feedback persistence and owner isolation; v3 migration preserving existing records; full suite, QA build, signature/security packaging, private upload readback.
