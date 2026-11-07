# Photo measurement correction and versioned review

Implement the researched workflow on the existing participant and fabrication web pages. Preserve the approved layout, camera illustrations, original uploads, authentication boundaries and all eleven measurement definitions. Retain the current scalar card calibration; do not claim projective or medical accuracy validation.

## Approach
Extend the existing Python pipeline to return image-space geometry, rather than reverse engineering flattened JPGs or adding a new measurement engine. A lightweight SVG selection layer sits above each decoded image. Only four card corners and wrist point may be overridden; arbitrary dimension values are not editable. User requested implementation after reviewing these alternatives and the research.

Corrections create a new, immutable result revision under the same case. Original files, overrides, measurements and generated assets belong to that revision. A revision selector exposes previous versions; review completion applies only to the current complete revision. Recalculation and resubmission clear prior review. Existing records migrate without losing files. Compare-and-set revision checks reject stale writes. Bound retained revisions to 20 per case.

Participant receipt status exposes requested view(s) and reason, never names, measurements or photos. Receipt plus upload key are required for partial retakes; reuse the other original unchanged. Keep receipt/key in session storage for same-tab reload only, with an explicit forget/new-person action. Submission confirmation shows both thumbnails and selected hand. Detect byte-identical images before accepting them as the two distinct views.

## UI
Maker: filter review states; select dimension to highlight its exact segment and switch view; zoom and original/annotated toggle; calibration and wrist-method facts; guided card/wrist point selection with undo/reset/cancel; explicit save-and-recalculate action; revision selector; review checkbox and mark complete; choose requested views and give a reason. Unsaved edits block navigation and printing. Download filenames identify revision.
Participant: final two-photo confirmation, receipt retained on reload, partial retake form for requested views, clear requested reason and same case number.

## Limits
No new CAD parameters, 3D scanning, invented confidence percentages, production approval or calibration algorithm changes. Test fixtures never imply live inference or real measurement accuracy. Existing legacy results without geometry remain viewable, with a recalculate-originals path to gain geometry.
