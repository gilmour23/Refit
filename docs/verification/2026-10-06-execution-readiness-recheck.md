# 개발 실행 경로 재점검

기준 소스는 개발 브랜치의 `22615860a545f1492075dbf522b98e9ca03faad8`입니다. 설치 버전 안내에 그치지 않고 깨끗한 소스, 앱 시작 경로, CI와 설정 일치, 기록 보존 범위를 확인했습니다.

## 실행한 검증

- Git에 기록된 소스만 임시 폴더로 추출했습니다. 기존 `local.properties`, 빌드 결과, 서명 파일은 복사하지 않았습니다. Linux·JDK 17·Gradle Wrapper 8.13·SDK Platform 36 환경이며 SDK와 의존성 다운로드 캐시는 재사용했습니다. Windows 첫 설치나 새 네트워크 다운로드를 실행했다는 의미는 아닙니다.
- `sdk.dir`만 설정하고 서버 URL·키 없이 전체 테스트·debug 조립·Lint를 실행했습니다. 기준 소스의 **67개 테스트, 실패·오류·건너뜀 0**, 빌드 성공, Lint 오류 0·경고 12를 확인했습니다.
- 음성 안내 코드는 있는데 Android 11 이상에서 필요한 `TTS_SERVICE` 조회 선언이 원본과 병합 Manifest에 없었습니다. 수정 전 병합 Manifest 검사에서 실패를 확인했습니다. [Android의 TTS 엔진 조회 요구사항](https://developer.android.com/training/package-visibility/use-cases)에 맞춰 해당 서비스만 조회하도록 선언했습니다.
- CI가 설치하던 Build Tools 36.0.0을 실제 [AGP 8.11 기본값 35.0.0](https://developer.android.com/build/releases/agp-8-11-0-release-notes#compatibility)으로 맞췄습니다. compileSdk는 기존 36을 유지했습니다.
- 새 시작 검증은 화면 컴포넌트만 렌더하지 않고 실제 `MainActivity`·기본 Repository·ViewModel을 실행하고 Activity를 재생성합니다. CI와 같은 가짜 서버 설정에서 로그인 화면이 열리고 운동 화면 접근이 차단되는지 확인했습니다. 실제 서버 로그인 요청을 실행하는 테스트는 아닙니다.
- 첫 설정된 시작 검증은 Robolectric이 Manifest의 startup provider를 실행하지 않아 실패했습니다. 실제 병합 Manifest에는 Supabase·Settings initializer가 이미 등록돼 있습니다. 테스트에서 Android가 실행하는 실제 `InitializationProvider`를 Activity보다 먼저 실행하도록 보완했고, 앱의 인증·저장 방식은 바꾸지 않았습니다.
- 수정 후 가짜 서버 설정으로 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:processQaManifest --offline -Pkotlin.incremental=false`를 실행했습니다. **68개 테스트, 실패·오류·건너뜀 0**, debug 조립 성공, Lint 오류 0·경고 12, QA Manifest 처리 성공을 확인했습니다.
- 최종 테스트 코드로 서버 설정 없는 앱 시작·Activity 재생성 검증도 다시 실행해 1개 테스트 통과를 확인했습니다.
- debug와 QA 병합 Manifest에 TTS 조회 선언과 기존 Supabase·Settings 초기화 등록이 들어 있습니다. `QUERY_ALL_PACKAGES` 권한은 추가하지 않았습니다.
- 기준 커밋의 원격 CI는 Android 빌드와 PostgreSQL 소유권 검증 모두 통과했습니다: [Actions 실행](https://github.com/gilmour23/Refit/actions/runs/37345785486). 수정 후 원격 실행 결과는 개발 브랜치의 동일 커밋 Actions에서 별도로 확인합니다.

## 남은 실제 환경 검증

- Windows Android Studio 설정, 에뮬레이터 실행, 실제 서버 계정 로그인.
- 기기의 한국어 TTS 출력·큰 글자·한 손 조작.
- 운동 종료 후 저장·앱 재시작 후 같은 설치의 기록 조회·기존 서명으로 휴대폰 업데이트.
- 실제 센서 규격·연결·수신·끊김·재연결. 기본 소스는 여전히 Mock입니다.

현재 앱은 새 기기의 서버 기록 다운로드나 전체 환자 메타데이터 복원을 제공하지 않습니다. 설치·GitHub 인증·소스 업데이트·기기 관찰의 역할과 처음 실행할 순서를 개발 문서에 명시했습니다. 실제 환경의 미실행 항목은 통과로 표시하지 않습니다.
