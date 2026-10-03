# Re:Fit V0.4 Foundation

MOONG TechThon용 Re:Fit Android MVP입니다.

현재 브랜치의 목표는 단순 UI 데모가 아니라 다음 흐름이 실제로 연결되는 앱 기반을 만드는 것입니다.

```text
로그인
  ↓
사용자 ID
  ↓
재활 세션
  ↓
EMG 실시간 처리
  ↓
Room 로컬 저장
  ↓
Supabase 요약 기록 동기화
```

## 현재 구현

### 계정 / 사용자
- Supabase Auth 기반 이메일 로그인 및 회원가입
- Supabase SDK의 저장 세션을 이용한 로그인 상태 복원 구조
- 인증된 `userId`를 앱 전체 세션 소유권에 사용
- 로그아웃
- 사용자별 재활 기록 분리

### EMG
- Mock sEMG 데이터 소스
- BLE sEMG 데이터 소스 기반 구현
- GATT scan / connect / service discovery / notification 구독
- 패킷 파서 인터페이스
- INT16 Little Endian 샘플 파서
- 최근 샘플 실시간 그래프
- rolling RMS
- 세션 RMS
- MAV
- bipolar 신호를 고려한 peak magnitude

### 저장 / 동기화
- Room DB 기반 offline-first 세션 저장
- 사용자별 history 조회
- `LOCAL_ONLY / SYNCING / SYNCED / FAILED` 상태
- 세션 결과를 Supabase에 요약값으로 upsert하는 동기화 레이어
- `(user_id, client_local_id)` 기반 중복 업로드 방지
- 네트워크 오류 시 로컬 세션 유지

### Supabase 보안
- `profiles`, `rehab_sessions` 스키마
- Row Level Security
- 사용자 본인 데이터만 읽기/쓰기 가능
- Android에는 publishable key만 사용
- raw EMG를 DB row 단위로 업로드하지 않음

## Supabase 설정

프로젝트 루트의 `local.properties`에 아래 값을 추가합니다.

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx
```

`local.properties`는 Git에 포함되지 않습니다.

Supabase SQL Editor에서 다음 파일을 실행합니다.

```text
supabase/schema.sql
```

## BLE 센서 연결

BLE 기반 구현은 들어가 있지만 실제 센서마다 다음 정보가 다르므로 임의 값을 코드에 넣지 않았습니다.

- BLE Service UUID
- Notify Characteristic UUID
- Device name/prefix
- packet byte layout
- signed/unsigned 여부
- sampling frequency
- ADC → voltage/EMG 단위 변환식

현재 `SessionViewModel`의 기본 데이터 소스는 안전한 개발용 `MockEmgDataSource`입니다.

실제 센서 사양이 확정되면:

```text
BleEmgConfig
+ EmgPacketParser
+ BleEmgDataSource
```

를 구성해 동일한 `EmgDataSource` 인터페이스로 교체합니다.

INT16 Little Endian raw samples를 전송하는 센서라면 `Int16LittleEndianEmgPacketParser`를 바로 사용할 수 있습니다.

## Room 데이터

세션마다 주요 값은 다음과 같습니다.

- local ID
- cloud remote ID
- clientLocalId
- userId
- 생성 시각
- 운동 종류
- 운동 시간
- 샘플 수
- RMS
- MAV
- peak activation
- sync 상태
- 수정 시각

Room schema version은 2이며, V0.3 schema version 1에서의 migration이 포함되어 있습니다.

## 데이터 정책

실시간 raw EMG는 운동 중 메모리에서 처리합니다.

V0.4에서는 Supabase DB에 다음 요약 결과만 저장합니다.

- 운동 시각
- 운동 종류
- 운동 시간
- sample count
- RMS
- MAV
- peak activation

raw EMG 장기 저장이 필요해지면 DB row 단위가 아니라 파일(CSV/binary) + Storage 구조로 확장합니다.

## 실행

1. Android Studio에서 저장소를 Clone 합니다.
2. JDK 17 이상을 사용합니다.
3. `local.properties`에 Supabase URL / publishable key를 입력합니다.
4. Supabase에서 `supabase/schema.sql`을 적용합니다.
5. Gradle Sync를 수행합니다.
6. Android 8.0(API 26) 이상 기기 또는 Emulator에서 실행합니다.
7. 회원가입/로그인을 수행합니다.
8. 개발 단계에서는 Mock EMG로 재활 세션을 실행합니다.
9. 운동 종료 직후 결과는 Room에 저장되고, Supabase 설정/네트워크가 정상이라면 cloud sync를 시도합니다.
10. 앱을 다시 실행하면 Supabase Auth session state에 따라 로그인 상태를 복원합니다.

## 실제 센서 적용 전 남은 외부 입력

코드 자체에서 추측하면 안 되는 값 두 종류가 남아 있습니다.

1. **Supabase 프로젝트**
   - 실제 project URL
   - publishable key
   - `supabase/schema.sql` 적용

2. **실제 sEMG 센서 프로토콜**
   - Service / Characteristic UUID
   - sampling rate
   - packet layout
   - scale/calibration

이 값이 제공되면 Mock 데이터 소스를 실제 BLE 소스로 전환할 수 있습니다.

## 주의

현재 RMS/MAV/peak 및 세션 간 RMS 비교는 MVP 기능 검증용입니다. 실제 임상적 회복 정도를 판단하려면 전극 위치, 측정 자세, MVC 등 normalization 기준, 전처리 프로토콜을 별도로 고정해야 합니다.


## 2차 검증 (2026-10-03)

로그인 상태 복원과 사용자별 로컬 기록 저장을 보완했습니다. 오프라인 로그아웃 시 인증 정보를 제거하고, 로그아웃·계정 변경 시 이전 사용자 상태를 정리합니다. 화면 회전과 일시적인 인증 로딩은 진행 중인 운동 상태를 유지합니다.

- 단위·통합 테스트 17개 통과
- Debug APK 빌드 성공
- 상세 검증: [Phase 2 identity verification](docs/verification/2026-10-03-phase2-identity.md)
- 실제 서버 로그인·기기 검증은 Supabase 프로젝트 설정 후 진행해야 합니다.
