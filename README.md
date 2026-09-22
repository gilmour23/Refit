# Re:Fit V0.2

MOONG TechThon용 Re:Fit Android MVP입니다.

## 현재 구현
- Jetpack Compose UI
- Mock sEMG 데이터 생성
- 실시간 EMG 그래프
- 재활 시작/종료
- 운동 시간 측정
- 최근 200개 샘플 실시간 표시
- 전체 세션 샘플 기반 RMS 계산
- 전체 세션 샘플 기반 MAV 계산
- 최대 근활성 계산
- 운동 종료 후 세션 결과 화면

## 현재 데이터 흐름

```text
Mock sEMG
  ↓
EmgDataSource
  ↓
SessionViewModel
  ├─ 최근 200 sample → 실시간 그래프
  └─ 전체 session sample → RMS / MAV / Max
                              ↓
                         ResultScreen
```

## 아직 구현하지 않은 항목
- 실제 BLE 연결
- 실제 sEMG 필터링
- Room DB
- 과거 세션 비교
- Rule-based recommendation
- Vision/CAD 연동
- Supabase 연동

## 실행
1. Android Studio에서 이 저장소를 Clone 합니다.
2. Gradle Sync를 수행합니다.
3. Android Emulator 또는 Android 8.0(API 26) 이상 기기에서 실행합니다.
4. `재활 시작`을 누르면 Mock EMG가 실시간으로 표시됩니다.
5. `운동 종료`를 누르면 RMS, MAV, 최대 근활성, 운동 시간이 표시됩니다.

## V0.3 예정
- Room DB 기반 세션 저장
- History 화면
- 이전 세션과 비교
