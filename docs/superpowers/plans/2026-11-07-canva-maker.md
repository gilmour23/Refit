# Canva reference implementation

1. Replace maker presentation with charcoal case navigation, white data surface, blue selection and neutral photograph canvas. Remove obsolete layout stylesheet from the maker entrypoint only.
2. Align case-list metadata and state in explicit semantic elements; preserve authentication, requests, stage/revision controls, correction logic and all measurement definitions.
3. Update the isolated illustration-only preview. Run existing suites and inspect the maker UI in a browser at desktop and narrow widths, including correction and dimension selection.
4. Commit the focused changes; refresh the existing demo ZIP and preview images with clear labels. No production hosting or Android rebuild required by this design task.

## Verified result

25 Node tests and 36 Python tests passed. Browser illustration fixture verified search, retake-status filtering, width/thickness photo switching, four-corner selection (4/4) and save eligibility. At 1363×936, body and viewport extents match, image and SVG bounds both 342.75×457 at (455.625,347), and final dimension row ends at 865.891 within inspector bottom 866. A 390px iframe (375px content plus scrollbar) has no horizontal body overflow and stacks the work area. No real participants or computed numeric values appear in fixtures. Screenshots are illustration-only. Native camera/measurement accuracy, printing hardware and live production authentication were not newly validated.
