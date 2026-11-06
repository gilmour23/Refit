# Canva web design implementation

The user approved implementing the two Canva mockups on 2026-11-07: a mobile participant capture form and a separate desktop fabrication website. Keep the existing Python server and measurement pipeline; a static hosted replacement would lose server-side image processing and private participant records.

Reference designs:
- Maker: ReFit · 제작자 PC 웹사이트 · 최종 시안
- Participant: ReFit · 참가자 촬영 안내 · 최종 시안

Implement the white/navy/teal layout, restrained rules, clear labels, illustrated card and hand poses, prominent participant/hand identity, paired width/thickness rows, three length rows, and direct report/CSV controls. Render real records from existing authenticated APIs; mockup placeholders are not sample measurements. Keep all 11 measurement definitions, consent, original-file preservation, operator key separation, print validity checks, and stale-response protections.

Builder decisions: keep responsive browser HTML/CSS rather than Canva embeds or raster screens; preserve camera/library controls and data labels. Keep server connection detail within connection settings and use plain participant-facing status copy. No new platform, login provider, or public deployment is introduced by this styling change.

Acceptance: existing Python/Node suites pass; participant 430/360px widths and operator desktop layout are reviewed; package contains the updated pages, model and launcher, without participant data or keys. Physical device camera and successful measurement accuracy remain subject to real-device rehearsal.
