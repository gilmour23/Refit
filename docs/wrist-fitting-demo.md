# Wrist fitting prototype — 2026-11-01

Requested scope: a working demonstration before the measurement algorithm and hardware CAD are final.

Bottom navigation: 홈 · 보조기 · 운동 기록. The 보조기 tab is a primary app destination, reachable from Home and History without scrolling. It opens left/right selection → two local images or explicit example drawings → movable wrist guide lines → synthetic dimensions → rotatable open-cuff preview → optional JSON/STL export. It is not presented as an extra experience card below recent history.

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
Run `app` on the existing emulator. Select the bottom `보조기` tab. Choose `예시 그림으로 진행` if no photos are available. The UI retains explicit sample-data notices; moving the feature into primary navigation does not make the dimensions image-derived or the model a finalized orthosis.
