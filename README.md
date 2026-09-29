# Re:Fit V0.4

MOONG TechThon용 Re:Fit Android MVP입니다.

## 현재 사용자 흐름

```text
홈
 ↓
보조기·센서 확인
 ↓
sEMG 기준 측정
  - 3초 휴식
  - 3초 가벼운 수축
 ↓
운동 설명
 ↓
손목 폄 운동
  - 애니메이션 동작 안내
  - Mock sEMG 실시간 기록
 ↓
운동 종료
 ↓
세션 결과
 ↓
Room DB 저장
 ↓
재활 기록 / 같은 운동 RMS 추이
```

## 현재 구현

- Jetpack Compose + Material 3 UI
- Re:Fit 전용 theme
- 보조기·센서 확인 화면
- sEMG 기준 측정(calibration)
- 손목 폄 운동 애니메이션
- Mock sEMG 데이터 생성 및 실시간 그래프
- 최근 200개 샘플만 UI 그래프에 유지
- 전체 세션 지표는 누적 통계 방식으로 계산하여 raw sample list를 무한 보관하지 않음
- RMS / MAV / 최대 sEMG 신호값 계산
- Room DB 기반 세션 저장
- 저장 실패 상태 처리
- 같은 운동의 이전 RMS와 비교
- 같은 운동 기준 최근 RMS 추이
- Room DB v1 → v2 migration
- Android backup / device-transfer 제외
- cleartext traffic 차단
- hardened internal build type(`debuggable=false`)
- Gradle 8.9 Wrapper

## sEMG 표기 주의

현재 입력은 실제 BLE 센서가 아닌 **Mock sEMG**입니다.

따라서 현재 값은 임상적 근활성도, MVC%, 회복률 또는 정상 범위를 의미하지 않습니다. 실제 센서 연동 전까지 화면에서는 “sEMG 신호값”과 “Mock 데이터”로 명확히 표기합니다.

실제 BLE 연동 전 확정해야 할 항목:

- sEMG 센서 모델
- MCU / ADC
- sampling rate
- channel 수
- Service / Characteristic UUID
- packet format
- endian
- sequence / timestamp
- batching
- preprocessing / normalization 기준

## 데이터 흐름

```text
Mock sEMG
  ↓
EmgDataSource
  ↓
SessionViewModel
  ├─ 최근 200 sample → Live graph
  └─ streaming aggregates
       ├─ sample count
       ├─ sum of squares → RMS
       ├─ sum of absolute values → MAV
       └─ max value
            ↓
       RehabSessionResult
            ↓
       Room Database
            ├─ Result
            └─ History / Trend
```

## 로컬 빌드

현재는 **로컬 Gradle Wrapper 빌드가 canonical build path**입니다. GitHub-hosted runner가 안정화되기 전까지 PR 자동 CI에는 의존하지 않습니다.

### Windows

JDK 17과 Android SDK가 설치된 상태에서:

```powershell
.\scripts\build_internal.ps1
```

이 스크립트는 빌드 후 다음 항목을 자동 검증합니다.

- `debuggable=false`
- Compose `PreviewActivity` 미포함
- `allowBackup=false`
- `usesCleartextTraffic=false`
- `INTERNET` 권한 없음
- SHA-256 출력

직접 Gradle만 실행하려면:

```powershell
.\gradlew.bat --no-daemon :app:assembleInternal
```

생성 파일:

```text
app/build/outputs/apk/internal/app-internal.apk
```

### macOS / Linux

```bash
./scripts/build_internal.sh
```

Unix 스크립트도 동일한 hardening 검증과 SHA-256 출력을 수행합니다.

### GitHub Actions

워크플로는 현재 `workflow_dispatch` 수동 실행만 유지합니다. 저장소 소유 계정의 Actions/runner 문제가 정리되면 PR 자동 CI를 다시 활성화합니다.

## 빌드 타입

- `debug`: 개발·UI inspection용
- `internal`: 데모/팀 배포용
  - `debuggable=false`
  - Compose `ui-tooling` 미포함
  - backup 비활성화
  - cleartext traffic 차단

현재 internal 빌드는 개발 편의를 위해 debug signing config를 사용합니다. 외부 배포 또는 실제 사용자 데이터 사용 전에는 별도 release signing key를 적용해야 합니다.

## 아직 구현하지 않은 항목

- 실제 BLE 스캔·연결·재연결
- 실제 sEMG 센서 입력
- 실제 센서 기준 signal-quality 판정
- 실제 반복 횟수 / 세트 자동 인식
- 세션별 raw EMG 파일 저장
- 환자 ID 기반 기록 분리
- Vision 치수 측정
- Parametric CAD / STL 연동
- Supabase / cloud sync
- 임상 검증

## 다음 개발 단계

1. 실제 sEMG HW packet specification 확정
2. `BleEmgDataSource` 구현
3. Mock / BLE datasource 전환 구조 추가
4. 실제 센서 기반 calibration·signal quality 검증
5. Vision → CAD 파이프라인 연결
