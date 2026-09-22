# Re:Fit V0.3

MOONG TechThon용 Re:Fit Android MVP입니다.

## 현재 구현
- Jetpack Compose UI
- Mock sEMG 데이터 생성
- 실시간 EMG 그래프
- 재활 시작/종료
- 운동 시간 측정
- 최근 200개 샘플 실시간 표시
- 전체 세션 기반 RMS / MAV / 최대 근활성 계산
- 운동 종료 후 결과 화면
- Room DB 기반 세션 저장
- 이전 세션 목록 조회
- 이전 세션 대비 RMS 변화 표시

## 데이터 흐름

```text
Mock sEMG
  ↓
EmgDataSource
  ↓
SessionViewModel
  ├─ 최근 200 sample → 실시간 그래프
  └─ 전체 session sample
          ↓
      RMS / MAV / Max
          ↓
   RehabSessionResult
          ↓
      Room Database
          ├─ ResultScreen
          └─ HistoryScreen
```

## Room 데이터
세션마다 다음 값을 저장합니다.
- 생성 시각
- 운동 이름
- 운동 시간
- 샘플 수
- RMS
- MAV
- 최대 근활성

현재 Room 버전은 2.8.5를 사용합니다.

## 주의
현재 세션 간 RMS 비교는 MVP 데모용입니다. 실제 sEMG 임상 비교에는 전극 위치 통제, 신호 전처리, normalization 기준이 추가로 필요합니다.

## 아직 구현하지 않은 항목
- 실제 BLE 연결
- 실제 sEMG 필터링
- 세션별 원시 EMG 파일 저장
- Rule-based recommendation
- Vision/CAD 연동
- Supabase 연동

## 실행
1. Android Studio에서 이 저장소를 Clone 합니다.
2. Gradle Sync를 수행합니다.
3. Android Emulator 또는 Android 8.0(API 26) 이상 기기에서 실행합니다.
4. `재활 시작`을 눌러 Mock EMG 세션을 진행합니다.
5. `운동 종료` 후 결과가 Room DB에 저장됩니다.
6. `이전 기록`에서 저장된 세션을 확인할 수 있습니다.

## V0.4 예정
- 실제 BLE 스캔 및 연결
- BLE 기반 sEMG 데이터 소스
- Mock / BLE 데이터 소스 전환 구조
