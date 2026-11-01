# Wrist fitting prototype — 2026-11-01

Requested scope: a working demonstration before the measurement algorithm and hardware CAD are final.

First use opens brace preparation, not exercise Home. Until a brace setup record exists for the current owner, restored Home/Session routes are gated, and the setup screen has no Home/History tabs to bypass it. The flow is left/right selection → two local images or example drawings → wrist guide lines → synthetic dimensions → rotatable open-cuff model and optional JSON/STL export → explicit demo brace registration → exercise Home. After registration the bottom navigation is 홈 · 보조기 · 운동 기록.

The record is explicitly `demo`, not physical manufacture completion. It stores only side and mode in private local preferences under an owner hash, without photos or real body dimensions. Commit must succeed before exercise is unlocked. Load failure leaves exercise locked and offers retry. Registration persists across activity recreation and process restart; a new owner's setup is independent. Exercise preparation defaults to the registered wrist. Real production will require a separate verified physical-brace readiness state before real exercise; that is not simulated by this prototype.

After registration, the 보조기 tab opens 내 보조기 with the saved side, demo registration status, illustrative model and synthetic dimensions. It never automatically restarts creation. The visible 새 보조기 제작 button explicitly opens a separate CREATE_BRACE route. Cancel/back returns to the registered-brace view and preserves the old setup; only successful new registration replaces it. Restoring a completed view retains details, while restoring an explicitly opened creation route resumes that draft. Exercise Home remains the post-registration destination.

The source is based on `chore/offline-demo-20261101` at `08c75268f12ccad42696a4e57388ce7ece2f0745`. Existing exercise, login-free startup and local exercise history are reused without changing their data model.

The UI and exported JSON identify `data_mode=demo`, synthetic dimensions, guide lines unused by measurement, and an illustrative cuff unsuitable for patient use. Image selection does not infer dimensions. The guide uses normalized vertical image coordinates after EXIF orientation, with aspect-fit offsets respected; these are UI annotations, not yet the Vision API's pixel-coordinate handoff. The selected photos are decoded locally at bounded resolution and never uploaded or included in exports. Local document grants support recreation. The system document picker handles JSON/STL destinations and cancellation.

The preview and STL share the same closed, open-sided shell mesh. All dimensions are millimetres; STL does not itself carry a unit declaration, so use millimetres on import. This is a generic demonstration object, not a motion-assisting orthosis, validated fit, or completed hardware CAD.

Future work: replace synthetic fixture with the wrist measurement engine; agree anatomical reference lines and hardware attachment requirements; replace cuff template with validated CAD. Keep real measurement results distinguishable from existing demo records. Actual camera capture, measurement server calls and printer control are not implemented by this prototype.

Validation: CI runs the existing offline integration suite, a small-screen prototype flow/back-navigation test, and mesh/export checks (closed edges, consistent winding, numeric locale, explicit demo provenance), followed by the existing regression suite, lint and debug build. The host has no Android SDK, so compilation and Android rendering run on the repository CI runner.

Android Studio:
```powershell
git -c http.version=HTTP/1.1 fetch origin
git switch chore/wrist-fitting-demo-20261101
```
Run `app` on the existing emulator. A user without a setup record starts at brace preparation. Choose `예시 그림으로 진행` if no photos are available, complete the model step, then explicitly register the demo brace to reach exercise Home. The UI retains sample-data notices; registration does not mean that a physical orthosis has been manufactured.
