# ReFit design refresh implementation plan

**Goal:** 기존 환자 흐름을 유지하면서 공식 앱 화면에서 확인한 시각적 위계를 적용한다.
**Architecture:** 공유 Compose 테마·컴포넌트와 기존 화면만 수정한다. 상태·DB·서버는 유지한다.
**Tech Stack:** Kotlin, Jetpack Compose, Robolectric, Roborazzi.
**Spec:** ../specs/2026-10-26-design-refresh.md

- [x] 공식 Hinge Health 스토어 이미지와 삼성 헬스 화면을 직접 확인.
- [x] ReFitUi.kt의 글자·간격·탐색 표시 수정.
- [x] HomeScreen.kt에 동작 그림과 시작 영역을 배치.
- [x] SessionScreen.kt의 준비 단계와 운동 그림 영역 정돈.
- [x] ResultScreen.kt와 HistoryScreen.kt의 요약·목록 압축.
- [ ] 기존 PatientScreensTest로 선택·중단·재연결·상태 입력 및 글자 확대 확인.
- [ ] 전체 테스트·Lint·빌드와 실제 렌더 확인.
- [ ] 변경 내용을 별도 브랜치·PR에 보존하고 검증 한계를 보고.

주의: 데모·실제 센서 구분을 유지한다. 짧은 화면에서 동작 그림을 축소하더라도 행동 지시와 중단 버튼은 표시되어야 한다. 기록의 미입력 상태를 정상·회복으로 바꾸지 않는다.
