# Re:Fit UI Specification V0.4

## Product framing

Re:Fit is a **guided hand/wrist rehabilitation app with sEMG as supporting measurement data**.

The primary user task is not to inspect a sensor dashboard. The primary task is:

1. understand today's rehabilitation task,
2. perform the movement correctly,
3. finish the session,
4. review the recorded rehabilitation and sensor data.

sEMG is visible, but it does not dominate the exercise experience.

## Reference pattern

The interaction model follows common digital rehabilitation products:

- guided exercise content first,
- large movement/video area,
- simple repetition/set/session context,
- sensor or performance feedback as secondary information,
- clear session completion and history views.

## Screen hierarchy

### Home

Purpose: start today's rehabilitation quickly.

Content order:

1. Re:Fit
2. Today's rehabilitation
3. Today's program card
4. Actual weekly completion progress from Room history
5. Recent rehabilitation
6. Full record link

Avoid dashboard-style KPI walls.

### Exercise preparation

Purpose: understand the movement before starting.

Content order:

1. Back
2. Exercise title and explanation
3. Large movement visual
4. 10 reps / 5 sets / estimated time
5. Three essential instructions
6. Sensor readiness
7. Start exercise

### Live rehabilitation

Purpose: follow the movement.

Priority order:

1. Exercise movement visual
2. Current instruction
3. Session time
4. Sensor connection and current relative activation
5. Optional expandable sEMG waveform

The waveform is collapsed by default.

### Result

Purpose: confirm completion and inspect recorded values without implying clinical recovery.

Content order:

1. Rehabilitation complete
2. Session summary
3. sEMG record
4. Previous-session measurement comparison
5. Interpretation warning
6. Return home

Do not use recovery score, health score, or wording that implies RMS change equals recovery.

### Records

Purpose: review completed rehabilitation over time.

- Show total completed sessions.
- Show the latest session time.
- Only show a trend chart when 3 or more sessions exist.
- Use up to the latest 7 sessions for the chart.
- Keep raw session rows visible below.

## Layout

- Target phone reference: 390 x 844 dp class
- Horizontal margin: 20 dp
- Section spacing: 20–30 dp
- Card radius: 14–18 dp
- Primary button height: 54 dp
- Bottom actions must use Android navigation-bar padding.
- Pages with variable content must scroll.

## Visual language

- Background: #F7F8FA
- Surface: white
- Primary: #1769E0
- Primary soft: #EEF4FF
- Success: #1F8A5B
- Border: #E1E5EA
- Text: near black
- No gradients
- Minimal shadow
- One primary action per screen

## Current MVP constraints

- sEMG is mock data.
- Relative activation has no physical unit yet.
- Automatic repetition detection is not implemented.
- Automatic set tracking is not implemented.
- RMS/MAV/peak are measurement summaries, not recovery scores.
- Exercise visual is a schematic placeholder until validated video/animation is added.

## Prohibited labels

Do not display:

- 회복률
- 건강 점수
- 근육 회복 +X%
- AI가 판단한 상태
- RMS 증가 = 호전
- RMS 감소 = 악화
