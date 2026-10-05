# PC 설정과 개발 순서

## 소스와 실행 환경

Android Studio의 Get from VCS에서 `https://github.com/gilmour23/Refit.git`을 열고 `chore/development-baseline` 브랜치를 선택합니다. 비공개 저장소에 접근 가능한 계정을 사용합니다. Git CLI가 있다면:

```sh
git clone --branch chore/development-baseline https://github.com/gilmour23/Refit.git
cd Refit
```

- Android Studio: 프로젝트의 AGP 8.11.1을 지원하는 버전.
- Settings → Build Tools → Gradle: 배포는 Wrapper, Gradle JDK는 **17**. 터미널의 `JAVA_HOME`도 JDK 17로 설정합니다.
- SDK Manager: Platform 36, Build-Tools 36.0.0, Platform-Tools 설치.
- Gradle 8.13은 Wrapper가 받습니다. JAR·배포 ZIP 체크섬은 [공식 목록](https://gradle.org/release-checksums/)과 비교합니다.
- 첫 실행은 API 35/36 에뮬레이터에서 합니다. 앱의 최소 지원 버전은 API 26입니다.

Android Studio가 생성한 `local.properties`의 `sdk.dir`을 유지하고, 계정 실행에 필요한 기존 서버 값을 추가합니다. `local.properties.example`은 키 이름 확인용입니다.

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_REPLACE_ME
```

실제 로그인에는 유효한 설정이 필요합니다. service_role·secret key를 앱에 넣지 않습니다. 설정 파일은 Git에서 제외됩니다. 기존 서버에 스키마가 배포돼 있으므로 PC 설정을 위해 SQL이나 인증 정책을 다시 변경하지 않습니다.

## 첫 실행

Windows PowerShell, 프로젝트 루트:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -Pkotlin.incremental=false
```

macOS/Linux:

```sh
./gradlew --version
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -Pkotlin.incremental=false
```

Gradle 8.13·JDK 17과 BUILD SUCCESSFUL을 확인합니다. Android Studio에서 `app`과 에뮬레이터를 선택하고 Run을 누릅니다. 로그인 후 홈 → 준비 → 시도·휴식 → 결과 → 기록을 확인합니다. 기본 소스는 계속 Mock입니다.

보고서: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. 실제 Compose 화면 렌더: `app/build/outputs/patient-screens/`.

CI는 가짜 URL·공개 테스트 키를 사용한 HTTP 격리 테스트·Lint·debug 조립과 별도 PostgreSQL 소유권 검증을 실행합니다. CI APK는 **빌드 확인용**이며 실제 로그인 설정이 없습니다. 기기·센서 검증은 CI 통과와 별개입니다.

## 휴대폰과 기존 기록

현재 전달한 0.5.3 QA APK는 디버깅이 꺼져 있고 기존 개발 서명을 유지합니다. 새 PC의 debug/qa 서명은 다르므로 같은 `com.refit.app` 패키지의 업데이트 설치가 거부될 수 있습니다. `qa`도 해당 PC의 debug 서명을 사용합니다.

**업데이트 실패를 해결하려고 기존 앱을 삭제하거나 데이터를 지우지 않습니다.** 새 환자 메타데이터는 기기 로컬에만 있고 앱 백업도 꺼져 있습니다. 서명 키는 Git에 넣지 않습니다. 첫 실행은 에뮬레이터나 기존 앱이 없는 테스트 기기에서 합니다. 기존 휴대폰 업데이트는 동일 서명으로 빌드할 수 있는 환경에서 별도로 진행합니다.

테스트 휴대폰에서 개발자 옵션·USB 디버깅을 켜고 USB로 연결한 뒤 기기에서 연결을 허용합니다. Android Studio의 기기 목록에서 휴대폰을 선택해 Run을 실행합니다. 이후 같은 PC의 같은 서명을 유지하면 수정 후 Run으로 업데이트할 수 있습니다.

## 반복 작업

작업 전 `git status`, `git log -1 --oneline`으로 상태와 커밋을 확인합니다. 문제나 기능마다 브랜치를 만들고 코드·테스트·필요한 문서를 함께 커밋합니다. PR 검토 후 통합합니다.

기기 문제는 커밋·앱 버전·기기·소스·재현 순서를 [기기 검증 양식](DEVICE_TEST.md)에 기록합니다. Android Studio Logcat에서 `package:com.refit.app`으로 앱 로그를 확인합니다. 요청/응답 전체나 인증·원시 EMG 로깅을 켜지 않습니다. 현재 앱에는 별도의 구조화된 센서 진단 로그가 없습니다.

관련 테스트 → 전체 테스트·빌드·Lint → 같은 기기 재현 순서로 검증합니다. 실행하지 못한 단계는 완료로 표시하지 않습니다.

## 개발 순서와 완료 기준

| 순서 | 작업 | 완료 기준 |
| --- | --- | --- |
| 1 | PC 실행 환경 | Wrapper 테스트·빌드·Lint, 에뮬레이터에서 안내와 기록 확인 |
| 2 | 실제 센서 | 규격 기반 파싱, 실물 수신, 권한 거부·끊김·재연결 확인 |
| 3 | 한 운동 전체 흐름 | 실제 소스로 시작·휴식·중단·종료, 저장·재실행·기록 조회 |
| 4 | 환자 사용성 | 치료사가 안내한 동작, 큰 글자·한 손 조작·음성·중단 관찰 |
| 5 | 기록과 배포 | 새 필드의 서버 동기화 범위·마이그레이션, 정식 서명·배포 채널 |

## 센서 입력

`SessionViewModel`은 `MockEmgDataSource`를 사용합니다. `BleEmgConfig`, `EmgPacketParser`, `BleEmgDataSource`는 기반입니다. 현재 앱에는 실물 소스로 전환하는 사용자 설정·센서 선택 흐름이 없습니다.

| 입력 | 필요한 내용 | 상태 |
| --- | --- | --- |
| 식별 | 제조사·모델·펌웨어, Bluetooth 전송 여부 | 미확정 |
| 연결 | 광고 정보, Service·Notify/Indicate UUID, 시작/중지 명령·MTU | 미확정 |
| 패킷 | 헤더·길이·채널·바이트 순서·signed 여부·순번·시간 | 미확정 |
| 샘플링 | 채널별 Hz·패킷당 개수·누락·재연결 처리 | 미확정 |
| 단위 | ADC 변환·필터링·보정 조건 | 미확정 |
| 재현 자료 | 사양서, 익명 개발용 패킷과 예상 변환값 | 미확보 |

`Int16LittleEndianEmgPacketParser`는 헤더 없는 signed INT16 LE 배열용 예시입니다. 실제 형식으로 정상·빈/잘린 패킷·여러 샘플·경계값·채널·누락 테스트를 작성한 뒤 실물 수신과 비교합니다. 규격으로 확인할 수 없는 누락을 측정 가능하다고 주장하지 않습니다.

UUID·단위·임계값을 추측하지 않습니다. 센서 신호를 움직임 횟수·회복률로 변환하지 않습니다. 운동·보조기/전극 위치·시도와 휴식 시간은 프로젝트의 임상 입력을 별도로 확보합니다.
