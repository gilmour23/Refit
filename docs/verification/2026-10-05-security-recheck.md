# Re:Fit 0.5.1 보안 재점검 — 2026-10-05

## 다운로드 확인

이전에 저장한 ReFit-0.5.0.apk의 파일 항목과 18,314,093 bytes 크기를 확인했다. 파일이 삭제된 상태는 아니다. 사용자가 받은 만료 메시지의 원인을 서버 로그로 확정하지는 못했다. 공식 파일 다운로드 경로를 제공하고, 보안 설정을 보강한 새 APK와 동일 APK를 담은 ZIP을 별도로 저장한다. 사용자 휴대전화에서 링크를 누르는 과정은 직접 검증하지 않았다.

## 수정한 배포 설정

- versionCode 7 / versionName 0.5.1, applicationId com.refit.app.
- release 설정을 기반으로 qa 빌드 유형을 추가했다. 앱·JNI 디버깅을 끄고 debug 전용 Compose 테스트·미리보기 Activity를 제외한다.
- HTTP 평문 통신을 명시적으로 거부한다.
- allowBackup=false와 fullBackupContent=false를 유지·명시한다. Android 12 이상에서는 cloud-backup 및 device-transfer 각각에 대해 9개 저장 영역 전체를 제외한다. 제조사별 기기 이전 동작을 고려한 설정이다.
- 기존 0.4.1/0.5.0과 같은 개발 서명 키를 사용한다. 앱 ID와 서명을 유지하고 버전 코드를 올려 업데이트 조건을 충족한다. 삭제 후 재설치하면 기기 내 기록을 잃을 수 있다.
- 기능, 환자 화면, Room 스키마, 서버 테이블과 RLS는 변경하지 않았다.

## 실제 APK 검사

| 항목 | 확인 결과 |
| --- | --- |
| 파일 크기 | 13,360,539 bytes |
| APK SHA-256 | f6da038f14519ca607e968148c669824a21b77315d2eb8c3301f633f591cdaeb |
| 서명 인증서 SHA-256 | ccc640c00bb3e199b9e5f5def344fd9e7ca7dfd815e61764777036df8aeb6ace |
| 서명 검증 | APK Signature Scheme v2 검증 통과 |
| 디버깅 | 비활성. Manifest에 속성이 없으며 Android 기본값 false, QA BuildConfig.DEBUG=false |
| 백업·평문 통신 | 비활성 및 백업 제외 리소스 연결 확인 |
| 테스트용 Activity | ComponentActivity 및 PreviewActivity 없음 |
| 외부에 공개된 구성 요소 | 런처 MainActivity, DUMP 권한으로 보호된 ProfileInstallReceiver |
| 데이터 제공자·Room 서비스 | exported=false |
| 비밀 값 검사 | sb_secret 형식 키, service_role 문자열, keystore/jks 파일 발견되지 않음 |
| ZIP 검사 | CRC 및 내부 APK SHA-256 일치 |

APK 내 Manifest와 실제 압축 리소스의 연결을 검사했다. QA 리소스 최적화로 XML 경로가 짧아지는 것을 반영해 리소스 테이블에서 실제 경로를 확인했다. 디버깅 검사기는 기존 0.5.0에서 실패하고 최종 0.5.1에서 통과했다. 백업 제외 리소스가 없었던 중간 APK의 검사도 실패한 뒤 최종 APK에서 통과했다.

## 인증·기록·동기화 및 서버 검사

현재 코드의 로그아웃 즉시 계정 상태 폐기, 저장 토큰 삭제, 로그인/로그아웃 직렬화와 늦게 도착한 인증 이벤트 차단을 확인했다. 기록 조회·불편감 수정·동기화 상태 변경은 로컬 ID와 소유 계정 ID를 함께 검사한다. 동기화 요청 직전에 현재 인증 계정을 다시 확인하며 최종 서버 접근은 RLS가 제한한다.

운영 Supabase에서 환자 데이터 대신 정책·권한 메타데이터만 조회했다. profiles 및 rehab_sessions 모두 RLS가 켜져 있고, SELECT/INSERT/UPDATE/DELETE는 authenticated 역할의 auth.uid()와 기록 소유자를 비교한다. UPDATE에는 USING과 WITH CHECK가 모두 있다. anon 테이블 권한은 없다. public SECURITY DEFINER 함수·뷰와 Storage 버킷은 없다.

| 실제 API 읽기 요청 | profiles | rehab_sessions |
| --- | --- | --- |
| 로그인 없이 조회 | 401 / 42501 | 401 / 42501 |
| alg=none 위조 JWT로 조회 | 401 / PGRST301 | 401 / PGRST301 |

기존 사용자 기록에 쓰기·삭제 요청을 보내지 않았다. 이번 점검에서 새 실제 사용자 두 계정의 JWT로 교차 접근을 재시험하지는 않았다. 로컬 계정 분리와 로그아웃·동기화 경합은 자동 회귀 검사에 포함한다.

## 검증

최종 백업 규칙을 반영한 전체 단위·통합·Compose 검사 51개가 통과했다. 실패 0, 오류 0. qa APK 빌드 및 lintVitalQa 통과, git diff --check 통과. 배포 설정에 대한 별도 읽기 전용 코드 리뷰에서도 추가 결함은 발견되지 않았다.

실행: gradle :app:testDebugUnitTest :app:assembleQa -Pkotlin.incremental=false

기존 Room Migration 인자 이름과 BLE API 폐기 예정 경고, 일부 네이티브 라이브러리의 심볼 제거 경고는 남아 있다. 검사는 실기기 설치·제조사별 백업 구현·전체 의존성 취약점 감사까지 포함하지 않는다.

## 남아 있는 사항

1. 서버 보안 Advisor에 Leaked Password Protection Disabled 경고 1개가 있다. 이전 10월 4일의 “경고 없음” 기록과 달리 현재 상태는 미해결 경고가 있는 상태다. 유출된 비밀번호 차단은 Supabase Pro 이상 요금제에서 제공되며, 이번에는 요금제나 서버 인증 설정을 변경하지 않았다.
2. 로그인 토큰과 Room 기록은 앱 전용 내부 저장소에 있지만 별도의 Keystore 기반 토큰 암호화 및 DB 암호화는 구현하지 않았다. 앱 샌드박스 보호와 추가 저장 암호화는 구분해야 한다.
3. 업데이트 호환성을 위해 개발 인증서를 유지했다. 정식 배포에는 관리되는 정식 서명 키와 그에 맞는 전환 계획이 필요하다.
4. 휴대전화의 실제 업데이트와 로그인, 기기 보안 상태, 실제 BLE 센서 및 임상 사용 검증은 완료하지 않았다. 현재는 내부 검토용 데모다.

## 근거 문서

- [Android 디버깅 위험](https://developer.android.com/privacy-and-security/risks/android-debuggable)
- [Android application 속성 및 디버깅 기본값](https://developer.android.com/guide/topics/manifest/application-element)
- [Android 백업·기기 이전 제외 규칙](https://developer.android.com/identity/data/autobackup)
- [Supabase 비밀번호 보호 설정과 요금제 조건](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
