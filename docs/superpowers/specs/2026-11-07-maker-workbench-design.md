# Fabrication workbench design correction

User explicitly requested the researched reference sites be reflected in the actual design after rejecting the earlier page. Scope: restructure the existing maker website, preserving working measurement/revision/security behavior and all eleven definitions. Participant capture remains unchanged.

## References observed
- Spentys, How to correct your scan: https://help.spentys.com/how-to-correct-your-scan . Viewed actual correction screenshot. Large persistent work object, visible stage navigation, instructions close to the active task, contextual landmark guide, save separated from editing.
- ImageMeter editor: https://imagemeter.com/manual/editor/editor-screen-overview/ . Viewed official toolbar image. Tools attached to the image, active selection and undo close to the editing surface.
- TechMed autoprocess: https://techmed3d.zohodesk.com/portal/en/kb/articles/autoprocess-steps . Viewed returned-result review screen. Processing return and user confirmation are separate.
These are interaction/layout references, not a claim to replicate proprietary internals or 3D reconstruction.

## Implementation decision
Replace the scrolling report layout with a desktop workbench: 204px case queue, compact identity/version row, explicit Photo / Dimensions / Review stages, persistent central image viewport and a 330px contextual inspector. Display fit scales the full photo into available space before zooming; image and SVG have identical bounds. Photo tools and undo sit on the canvas toolbar; unsaved corrections have a persistent action bar. Contextual inspector shows (1) detected reference/wrist/EXIF facts and correction guidance, (2) compact eleven-dimension table, or (3) current revision review, retake form and exports. Keep primary actions visible at the bottom.

Use navy for the app chrome, neutral grey only for the canvas, white for data surfaces and restrained teal for active controls. No dashboard cards, oversized blank report headings, gradients or fabricated measurements. Mobile stacks the same areas; print remains a complete sheet with both images and all dimensions.

Stage navigation is guidance, not an accuracy certification or a new approval gate. Existing server validation and current-version review remain authoritative. Dirty drafts survive stage changes but cannot be reviewed or printed. New-person selection resets stage; same-record refresh keeps the chosen stage. An explicit recalculate path remains available for old results without geometry.
