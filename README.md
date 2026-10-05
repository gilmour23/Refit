# Re:Fit 0.5.3

뇌졸중 후 손목을 펴려는 시도를 화면과 음성으로 안내하고 운동 기록을 남기는 Kotlin/Jetpack Compose Android 개발 프로젝트입니다.

## 현재 상태

- 홈 → 좌우·자세·착용 준비 → 준비 3초 → 시도·휴식 → 운동 후 상태 → 기록.
- 일시정지·중간 종료·백그라운드 전환과 한국어 음성 상태를 처리합니다.
- 기본 소스는 **모의 EMG**입니다. BLE 기반은 있지만 실제 센서 설정·실물 검증은 없습니다.
- 안내 구간 완료를 실제 움직임이나 회복률로 해석하지 않습니다. 시도 4초·휴식 6초·3회는 데모 설정입니다.
- Room v4가 계정별 기록을 저장하고 v1→v2→v3→v4 마이그레이션을 제공합니다.
- Supabase에는 기존 신호 요약만 업로드합니다. 좌우·통증·피로·새 안내 메타데이터는 기기 로컬에만 저장합니다. 다른 기기의 기록을 내려받는 기능은 없습니다.

## 개발 시작

[PC 설정과 개발 순서](docs/DEVELOPMENT.md)를 따라갑니다. 별도 Gradle 설치 없이 저장소의 Wrapper를 사용합니다.

```powershell
# Windows PowerShell, 프로젝트 루트, JDK 17
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -Pkotlin.incremental=false
```

macOS/Linux에서는 `./gradlew`를 사용합니다. SDK 36이 필요하며 최소 실행 기기는 API 26입니다. 계정 실행에는 기존 서버의 URL과 publishable key를 로컬 `local.properties`에 설정합니다.

## 작업 기준

코드와 테스트를 브랜치·커밋으로 관리하고 PC에서 실행합니다. 기기 문제는 같은 커밋의 재현 순서와 Logcat으로 확인합니다. APK는 확인된 빌드를 전달하는 데 사용합니다.

1. PC 테스트·빌드·에뮬레이터 실행.
2. 센서 규격 확보와 실물 샘플 수신·파싱.
3. 한 운동의 시작·중단·종료·저장·재실행·기록 조회.
4. 실기기 사용성과 환자·치료사 피드백 반영.

[기기 검증 양식](docs/DEVICE_TEST.md)에 실행한 결과만 기록합니다.

## 검증 기록

- [0.5.3 환자 흐름·66개 테스트](docs/verification/2026-10-05-patient-flow-053.md)
- [개발 환경 구성 검증](docs/verification/2026-10-05-development-baseline.md)
- [보안 재점검](docs/verification/2026-10-05-security-recheck.md)
- [배포된 서버 스키마·소유권 정책](docs/verification/2026-10-03-supabase-deployment.md)

실제 휴대폰·센서·한국어 음성 출력 검증은 남아 있습니다. 환자 화면에서 임상적 회복 판단을 제공하지 않습니다.
