# Re:Fit V0.1

MOONG TechThon용 Re:Fit Android MVP의 첫 버전입니다.

## 현재 구현
- Jetpack Compose UI
- Mock sEMG 데이터 생성
- 실시간 EMG 그래프
- 재활 시작/종료
- 운동 시간 측정
- 최근 200개 샘플 표시

## 아직 구현하지 않은 항목
- 실제 BLE 연결
- 실제 sEMG 신호 처리
- RMS/MAV
- Room DB
- 세션 결과 화면
- 과거 세션 비교
- Rule-based recommendation
- Vision/CAD 연동

## 실행
1. Android Studio에서 이 저장소를 Clone 합니다.
2. Gradle Sync를 수행합니다.
3. Android Emulator 또는 Android 8.0(API 26) 이상 기기에서 실행합니다.
4. `재활 시작`을 누르면 Mock EMG가 실시간으로 표시됩니다.

## 다음 버전
V0.2에서는 Session Result 모델, RMS/MAV 계산, 결과 화면을 추가합니다.
