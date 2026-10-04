# ReFit 보안 재점검

점검일: 2026-10-04 KST
대상 코드: 로컬 `74f897205c1ba466f014798c070b942a9270e425`, 원격 `13d8076b4f34309a01db5097e4d8916f90b34fd4`.
대상 서버: ReFit / `yqudfmmdderlbrzrfply` / 서울 리전.
대상 APK SHA-256: `07727ed36d02ffdd29bf7de663143fe43053df104533c261a938abc5180e372e`.

## 판정

검사한 서버 접근 제어와 클라이언트 키 구성은 통과했다. 이 점검 당시 중간 위험 1건을 재현했다. 같은 날 후속 작업에서 해당 로그아웃 문제를 수정하고 V0.4.1 APK를 빌드했다. [수정 및 검증 결과](2026-10-04-logout-fix.md)를 함께 확인한다. 전달된 APK는 개발 테스트용이며 정식 배포용 보안 검증을 마친 파일은 아니다. 아래 내용은 최초 점검 당시의 관측을 보존한다.

## 발견 사항

### 1. 중간 위험: 로그아웃 시작 후 서버 응답까지 이전 계정 접근이 유지됨

`AuthRepository.signOut()`은 SDK의 `signOut(LOCAL)` HTTP 요청을 기다린 뒤 `finally`에서 `clearSession()`을 호출한다. `AuthViewModel.signOut()`에는 로그아웃 진행 상태에 따른 화면 잠금이 없고, 홈 화면의 기록/운동 버튼도 그대로 활성화된다.

실제 Supabase Kotlin 3.8.0 SDK, 실제 SharedPreferences 저장소, 실제 AuthViewModel을 사용하고 외부 HTTP 응답만 지연시킨 일회성 테스트로 확인했다. 로그아웃 요청이 HTTP에 도달한 뒤 응답을 보류한 동안 다음 세 조건이 모두 유지됐다.

- 화면에 사용하는 AuthState가 이전 사용자의 Authenticated 상태였다.
- 이전 계정의 ViewModelStore가 지워지지 않았다.
- 세션 저장소에서 이전 세션을 다시 읽을 수 있었다.

HTTP 응답을 허용한 뒤에는 세 조건이 해제됐다. 따라서 네트워크가 느리거나 멈춘 상황에서 로그아웃을 누르고 다른 사람에게 기기를 건네면 이전 기록 화면을 사용할 수 있는 시간이 생긴다. 원격 공격자의 다른 계정 접근 문제와는 구분한다.

수정 기준: 로그아웃을 누르는 즉시 계정 화면/운동/동기화를 잠그고 계정 상태를 폐기해야 한다. 서버 세션 해지와 로컬 자격 증명 삭제도 함께 고려해야 하며, 새로운 계정 로그인과 겹칠 때 이전 로그아웃 작업이 새 세션을 지우지 않도록 검증해야 한다. 지연 응답, 오프라인, 취소, 앱 종료/재실행을 확인한다.

### 2. 배포 전 보완: 디버그 APK 및 기기 내 저장 보호

실제 APK manifest를 `aapt2`로 읽어 `debuggable=true`, `allowBackup=false`를 확인했다. Compose PreviewActivity도 디버그 의존성으로 포함돼 있다. `apksigner verify`는 성공했지만 인증서 이름은 Android Debug다. 정식 배포에는 별도 릴리스 빌드와 배포 서명이 필요하다.

SDK 기본 세션 관리자는 세션 토큰을 앱 전용 MODE_PRIVATE SharedPreferences에 JSON으로 저장한다. Room은 일반 SQLite이며 앱 차원의 추가 암호화를 구성하지 않았다. 정상 Android 앱 간에는 샌드박스로 보호되지만, 허용된 ADB 디버깅 또는 기기/앱 전용 저장소가 침해된 상황에서는 토큰과 기록을 읽을 수 있다. 일반 원격 사용자가 공개용 키만으로 이 파일들을 읽을 수 있다는 뜻은 아니다.

재활 기록의 민감도를 고려해 릴리스 배포 전 토큰의 Keystore 기반 보호, 로컬 기록 암호화, 공유 기기의 기록 보존/삭제 정책을 검토한다. 로그아웃 후 기록을 유지하는 현재 동작은 offline-first 설계이며, 다른 계정의 일반 앱 화면에는 표시되지 않는다.

## 다시 통과한 검사

| 항목 | 직접 확인한 결과 |
| --- | --- |
| Supabase 보안 Advisor | 지적 사항 없음 |
| 노출된 public 테이블 | profiles, rehab_sessions만 존재; 두 테이블 모두 RLS 활성화 |
| DB 권한 | authenticated는 SELECT/INSERT/UPDATE/DELETE만; anon 테이블 권한 없음; TRUNCATE 등 추가 권한 없음 |
| 소유권 정책 | 두 테이블 각각 SELECT/INSERT/UPDATE/DELETE에 auth.uid 소유권 제한; UPDATE는 USING과 WITH CHECK 모두 적용 |
| 실제 서버 SQL 회귀 검사 | 본인 기록 저장, 복합키 upsert와 ID 유지, 타 계정 조회/수정/삭제/삽입 및 소유자 변경 차단, 익명 RLS 차단 통과 |
| 실제 익명 REST 요청 | 두 테이블 각각 GET/POST/PATCH/DELETE 총 8건 모두 HTTP 401 / 42501로 거절 |
| 위조 JWT | 서명 없는 service_role 주장 JWT를 실제 REST API에서 HTTP 401로 거절 |
| 노출 우회 경로 | public SECURITY DEFINER 함수 없음; Storage bucket 없음 |
| APK 키 검사 | sb_publishable 키 1개; sb_secret 키나 service_role JWT 발견 없음 |
| Git 구성 | local.properties 추적 안 됨; 추적 파일에서 sb_secret 형식 키 발견 없음 |
| Android 구성 | 앱 데이터 provider/service 비공개; 추가적인 인증서 검증 우회나 명시적 HTTP 서버 URL 없음 |
| 앱 계정 분리 | Room 조회/상태 갱신에 userId 제한; 계정 변경 시 이전 ViewModel 폐기; SDK 소유자 확인 후 업로드 |
| 비밀번호 처리 | 앱 코드에 비밀번호 파일 저장 또는 로그 출력 없음; 기존 SDK 저장소 테스트에서 비밀번호 미보관 확인 |
| 기존 전체 테스트 | 새로 실행한 33개 모두 통과, 실패/오류/생략 0 |
| 추가 지연 로그아웃 점검 | 일회성 특성 확인 테스트 1개 통과: 문제의 현재 동작을 재현한 결과이며 안전성을 의미하지 않음 |

서버 SQL fixture와 임시 테스트 권한은 모두 트랜잭션에서 rollback했다. 이후 Auth 사용자, profiles, rehab_sessions 수는 각각 0이었다. live Supabase에서 disposable bootstrap은 실행하지 않았다. 앱 점검용 테스트는 외부 실제 로그인 요청을 하지 않았고 점검 후 빌드 소스에서 제거했다.

## 범위와 한계

- 이 검사는 관측된 접근 제어·코드·APK에 대한 재점검이다. 전체 취약점이 없다는 보증이나 정식 보안 인증이 아니다.
- 실제 두 사용자 계정의 JWT 발급/갱신/해지, 이메일 인증 및 실기기의 기기 잠금/복원/네트워크 동작은 아직 확인하지 않았다.
- 서버 비밀번호 강도, 유출 비밀번호 차단, JWT 만료 설정, 관리자 MFA 전체 설정은 이번 연결에서 확인하지 않았다.
- 전체 의존성의 CVE/SBOM 감사는 수행하지 않았다.
- 현재 기본 센서는 Mock이다. 향후 BLE 센서 사칭/페어링/패킷 검증은 실제 센서 통합 시 별도 확인해야 한다.
- 독립 코드 검토에서도 서버 계정 간 우회는 추가로 발견되지 않았으며, 지연 로그아웃과 디버그 배포 문제를 동일하게 지적했다.

## 참고한 공식 지침

- [Android debuggable](https://developer.android.com/privacy-and-security/risks/android-debuggable): 배포 시 디버깅 비활성화.
- [Android security checklist](https://developer.android.com/privacy-and-security/security-tips): 앱 전용 저장소, 권한 및 HTTPS 지침.
- [Supabase user sessions](https://supabase.com/docs/guides/auth/sessions): 로그아웃과 JWT/refresh token 수명 구분. 이미 발급된 access token을 즉시 무효화하는 보장은 별도 세션 확인이 필요하다.
