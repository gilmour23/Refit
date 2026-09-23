# Re:Fit UI Specification V0.4

## 1. 기준

- 기준 화면: 390 × 844
- 구현: Android Jetpack Compose / Material 3
- Layout grid: 8dp
- 기본 좌우 margin: 20dp
- Section 간격: 24–28dp
- 같은 section 내부 간격: 8–16dp
- Card radius: 12dp
- Button radius: 10dp
- 기본 버튼 높이: 52dp
- Secondary 버튼 높이: 48dp
- 그림자: 기본적으로 사용하지 않음
- Gradient: 사용하지 않음

## 2. 색상

| Token | Value | 용도 |
|---|---|---|
| Background | #F7F8FA | 앱 기본 배경 |
| Surface | #FFFFFF | 카드 / 정보 영역 |
| Text | #151A22 | 주요 텍스트 |
| Subtext | #667085 | 보조 텍스트 |
| Border | #E1E5EA | Divider / Border |
| Primary | #1769E0 | Primary CTA / 추이 |
| Primary Soft | #EEF4FF | 선택 / 강조 배경 |
| Success | #1F8A5B | 센서 연결 / 저장 |
| Success Soft | #EDF8F2 | Success background |
| Danger | #C43D3D | 운동 종료 |

## 3. Typography

- Screen title: 24sp Bold
- Section title: 16sp SemiBold
- Major metric: 20–24sp Bold
- Body: 15sp Regular
- Secondary body: 13sp Regular
- Caption: 11sp Regular

실제 제품 폰트 리소스가 확정되기 전에는 Android Sans Serif를 사용합니다.

---

# 4. Home

## 목적

앱을 열었을 때 오늘 해야 할 재활을 즉시 이해하고 시작할 수 있어야 합니다.

## 구조

1. App title
2. 사용자 이름 + 오늘 일정
3. Today Rehab Card
4. Weekly Goal
5. Recent Activity
6. History CTA

## Today Rehab Card

- Width: screen width - 40dp
- Padding: 16dp
- Radius: 12dp
- Border: 1dp
- Shadow: none

표시 정보:
- 오늘의 재활
- 손목 폄 운동
- 약 10분 · 5세트
- 센서 연결 준비
- 재활 시작

홈에는 RMS/MAV/최대값을 카드 형태로 대량 노출하지 않습니다.

---

# 5. Exercise Intro

## 목적

센서 데이터보다 먼저 사용자가 올바른 동작을 이해하도록 합니다.

## 구조

1. Back
2. 운동명
3. 한 줄 설명
4. 운동 영상/이미지 영역
5. 운동 방법 4단계
6. 반복/세트 목표
7. 센서 상태
8. 운동 시작

## Media area

- Height: 190dp
- 실제 운동 동작 콘텐츠가 준비되기 전에는 placeholder
- 향후 정면/측면 animation 또는 영상 사용 가능

---

# 6. Live Rehab

## 정보 우선순위

1. 현재 해야 할 동작
2. 반복/세트 진행
3. 센서 연결 상태
4. 근활성 데이터
5. EMG waveform
6. 운동 시간
7. 운동 종료

EMG는 주 화면의 주인공이 아니라 재활 동작의 측정 정보입니다.

## EMG 영역

- Graph height: 약 150dp
- 최근 200 samples 표시
- UI 재렌더링 빈도와 실제 sensor sampling rate는 분리
- 현재 Mock 데이터에서는 μV를 표시하지 않음
- 실제 센서 확정 후 physical unit 결정

---

# 7. Result

## 목적

운동 직후 사용자에게 세션이 저장되었음을 알리고 핵심 측정값을 확인시킵니다.

## 구조

1. 재활 결과
2. 운동명
3. 저장 완료 state
4. 측정값
5. 이전 RMS 비교
6. 데이터 해석 주의
7. 확인

## 금지 표현

- 회복률
- 건강 점수
- 근육 회복 +X%
- AI가 판단한 상태

RMS 변화는 단순 측정값 변화로 표현합니다.

---

# 8. History

## 목적

장기 경과 확인.

## 구조

1. 화면 제목
2. 최근 RMS 추이
3. 세션 목록

세션 목록은 큰 카드 반복이 아니라 단순한 list row + divider 방식으로 구성합니다.

각 row:
- 날짜/시간
- 운동명
- RMS
- 운동 시간

---

# 9. 다음 구현 우선순위

1. 실제 Android 기기에서 V0.4 레이아웃 확인
2. 390×844 외 화면에서 responsive 확인
3. 실제 운동 영상/이미지 추가
4. 실제 BLE sensor 결정
5. BLE protocol 연결
6. 실제 signal unit / preprocessing 확정
7. 반복 횟수 감지
8. therapist view
