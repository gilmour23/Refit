# Re:Fit V0.4

MOONG TechThon용 Re:Fit Android MVP입니다.

## V0.4 핵심 변경

UI를 단순한 센서 대시보드에서 실제 재활 서비스 흐름 중심으로 재설계했습니다.

현재 사용자 흐름:

\`\`\`text
홈
 ↓
오늘의 재활
 ↓
운동 설명
 ↓
운동 시작
 ↓
동작 안내 + Mock sEMG 측정
 ↓
운동 종료
 ↓
세션 결과
 ↓
Room DB 저장
 ↓
재활 기록 / 최근 RMS 추이
\`\`\`

## 디자인 방향

- Android Material 3 관습을 기본으로 사용
- 실제 디지털 재활 서비스처럼 다음 행동을 우선 노출
- 홈에서 전문 신호 지표를 과도하게 노출하지 않음
- 운동 화면에서는 동작 안내가 EMG 그래프보다 우선
- 결과 화면은 축하형 UI보다 세션 리포트 구조로 구성
- 기록 화면은 카드 대시보드보다 추이 + 세션 목록 구조 사용
- 배경 / Surface / Border를 명확히 구분하고 그림자와 그라데이션 최소화
- Primary Blue, Success Green, Danger Red만 기능적으로 사용

## 현재 구현

- Jetpack Compose UI
- Re:Fit 전용 Material 3 theme
- 홈 화면
- 운동 시작 전 설명 화면
- 실시간 재활 화면
- Mock sEMG 데이터 생성
- 실시간 EMG 그래프
- 재활 시작/종료
- 운동 시간 측정
- 최근 200개 샘플 실시간 표시
- 전체 세션 기반 RMS / MAV / 최대 근활성 계산
- 운동 종료 후 결과 화면
- Room DB 기반 세션 저장
- 기록 목록
- 최근 RMS 추이 그래프
- 이전 세션 대비 RMS 변화 표시

## sEMG 표기 주의

현재 입력은 실제 센서가 아닌 Mock 데이터입니다.

따라서 현재 UI에서는 실제 μV 단위 대신 상대값으로 취급합니다. 실제 BLE 센서가 결정되면 다음을 확정한 뒤 단위와 normalization 방식을 수정합니다.

- 센서 모델
- MCU / ADC
- sampling rate
- channel 수
- ADC range
- BLE packet format
- preprocessing / normalization 기준

## 데이터 흐름

\`\`\`text
Mock sEMG
  ↓
EmgDataSource
  ↓
SessionViewModel
  ├─ 최근 200 sample → Live EMG graph
  └─ 전체 session sample
          ↓
      RMS / MAV / Max
          ↓
   RehabSessionResult
          ↓
      Room Database
          ├─ Result
          └─ History / Trend
\`\`\`

## 아직 구현하지 않은 항목

- 실제 BLE 스캔 및 연결
- 실제 sEMG 전처리
- 실제 반복 횟수 자동 인식
- 실제 운동 영상 / 이미지
- 세션별 raw EMG 파일 저장
- Rule-based recommendation
- Vision / CAD 연동
- Supabase 연동

## 실행

1. Android Studio에서 저장소를 Clone 합니다.
2. Gradle Sync를 수행합니다.
3. Android Emulator 또는 Android 8.0(API 26) 이상 기기에서 실행합니다.
4. 홈의 재활 시작 버튼을 누릅니다.
5. 운동 설명 화면에서 운동 시작을 누릅니다.
6. Mock EMG 세션을 진행하고 운동 종료를 누릅니다.
7. 결과가 Room DB에 저장됩니다.
8. 홈의 재활 기록 보기에서 과거 세션과 RMS 추이를 확인합니다.
