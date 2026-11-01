# Wrist fitting prototype — 2026-11-01

Requested scope: a working demonstration before the measurement algorithm and hardware CAD are final.

Home → 보조기 제작 체험 → left/right selection → two local images or explicit example drawings → movable wrist guide lines → synthetic dimensions → rotatable open-cuff preview → optional JSON/STL export.

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
Run `app` on the existing emulator. Scroll below recent exercise history to open `보조기 제작 체험`. Choose `사진 없이 예시로 체험` if no photos are available.
