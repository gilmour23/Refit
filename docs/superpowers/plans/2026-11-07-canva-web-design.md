# Canva Web Design Implementation Plan

> For agentic workers: execute inline with superpowers:executing-plans.

Goal: implement the approved Canva screens in the working ReFit web demo.
Architecture: keep FastAPI and its private intake store and measurement worker; update the existing browser shells and stylesheet.
Tech stack: HTML/CSS, ES modules, Python/FastAPI.
Spec: ../specs/2026-11-07-canva-web-design.md

Constraints: no mock measurements in production, no user photos or credentials in the delivery archive, no change to the 11 dimensions or authorization contracts.
Review focus: narrow phone widths; Korean label wrapping; desktop photo/table alignment; printing; disabled controls and error states.

- [x] Add focused `vision/web/layout.css` overrides to preserve the existing state stylesheet and match the approved mobile/desktop layout. Keep existing IDs and state classes.
- [x] Update `vision/web/index.html`, `operator.html`, and participant status wording to match the approved wording, retaining real API behavior and consent.
- [x] Run the existing Python API/image and Node browser suites, check JavaScript syntax, and inspect responsive rendering.
- [x] Review the full diff, commit the change on the existing feature branch, and update the runnable ZIP with the verified source. Replace the existing delivery file without changing its identity.

Ruling: no new unit tests for reversible visual/copy changes; use existing behavioral coverage plus layout review. No additional approval round: the user explicitly approved these already-presented designs and instructed implementation.

Ruling: isolate the Canva layout in a small, readable stylesheet loaded after intake.css rather than rewriting the existing capture/state/print rules. This limits changes to the approved visual scope.

Verification: 24 Python API/security tests and 15 Node tests pass, JS syntax and CSS parsing pass. The new layout asset first returned 401 in the integration test, then passed after its exact public allowlist entry was added; private endpoints still reject upload/no keys. Managed Chrome verified 360px/430px participant pages with scrollWidth equal to clientWidth, top/side switching, visible consent and disabled submission, and the desktop fabrication layout. Operator visual review used an explicitly marked, nonfunctional fixture with blank values and no participant photos; it is excluded from the delivery. Physical camera, true successful-photo accuracy, live operator sign-in and printer hardware are not newly verified.

Final review: a fresh reviewer found no material regressions in asset authorization, printing, measurements or API behavior. Delivery version 6 contains 29 verified source/model/launcher files and no private record/credential/QA fixture. Browser preview stopped after inspection.
