# Galaxy 설치

## 빌드 환경

이것은 기존 앱을 수정하지 않고 새로 만든 Android Studio 프로젝트다. `android/` 폴더를 Open한다. 앱 소스는 Java이며 Kotlin 소스 파일이 없다. Gradle 9.4.1 wrapper, Android Gradle Plugin 9.2.1, JDK 17 이상(검증 JDK 21), compile/target SDK 36, min SDK 26(Android 8)을 사용한다. Gradle 내부의 Kotlin 지원이나 Java 라이브러리의 Kotlin 의존성은 앱 소스 언어와 별개다.

Android Studio SDK Manager에서 Android SDK Platform 36과 Build Tools를 설치한다. Android Studio가 자신의 SDK 위치에 맞게 local.properties를 생성하게 한다. 로컬 환경의 절대 SDK 경로를 Git에 넣지 않는다.

Firebase Console에서 Android 앱 `kr.infosphere.galaxyrelay`를 등록하고 client configuration을 `app/google-services.json`에 저장한다. 현재 작업 환경에는 이미 해당 새 Firebase 앱의 설정 파일이 준비되어 있다. JSON이 없으면 프로젝트는 열리고 빌드되지만 Backend 등록에는 Firebase 설정이 필요하다고 안내한다.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

설치용 APK는 repository의 `artifacts/GalaxyRelay.apk`이다. 실제 생성한 전용 RSA 3072-bit 키로 Release 서명했고 debuggable=false다. 개발·계측용은 `artifacts/GalaxyRelay-debug.apk`이다. 개인용 sideload이므로 설치에 사용하는 파일 앱/브라우저의 “출처를 알 수 없는 앱 설치”를 허용해야 할 수 있다. USB 디버깅을 허용한 자신의 기기에서는 `adb install -r GalaxyRelay.apk`를 사용할 수 있다.

현재 작업 환경은 `.secrets/galaxy-relay-release.p12`와 Git 제외 `android/release-signing.properties`에 서명 설정을 보관한다. 업데이트 APK를 만들려면 이 키와 비밀번호를 안전한 별도 위치에 백업한다. 키를 잃으면 같은 앱 설치를 유지한 채 업데이트할 수 없다. Debug와 Release는 서명이 달라 서로 덮어 설치하지 못한다. 서명 변경을 위한 앱 삭제는 로컬 기록·연결·키도 지우므로 처음부터 Release로 설치한다.

다른 계정에서 source ZIP을 빌드할 때에는 Android Studio → Generate Signed Bundle/APK에서 본인 키를 만들거나, JDK의 keytool을 PATH에 둔 뒤 repository root에서 다음을 실행한다. 실제 키와 설정은 source ZIP에 없다. script는 기존 키를 덮어쓰지 않는다.

```sh
python tools/create_release_signing.py
cd android
./gradlew :app:assembleRelease :app:lintRelease
```

결과는 `app/build/outputs/apk/release/app-release.apk`이다. signing key·설정 비밀번호를 Git/메신저/공유 ZIP에 넣지 않는다.

## 최초 연결

1. 앱의 Backend origin을 배포된 HTTPS 주소로 입력한다. `/api/...` 경로를 넣지 않는다.
2. `.secrets/cloudflare.json`의 INSTALL_SECRET을 등록 화면에 입력하고 “Backend 등록”을 누른다. secret은 서버 첫 등록 인증용이며 입력창에서 지워진다. 장기 token 및 FCM 검증 키는 Keystore로 암호화해 로컬 저장한다.
3. “알림 접근 허용”에서 Galaxy Relay를 활성화한다. 알림 본문을 중계하려면 이 권한이 필수다.
4. “Relay 알림 표시 허용”을 허용한다. 원본 앱 열기 fallback에 필요하다.
5. “전달할 앱 선택”에서 앱별로 선택한다. 카카오톡, Discord, Instagram, Samsung/Google Messages가 기본 대상이다. Relay 자신의 알림, ongoing notification, group summary는 제외한다.
6. “iPhone 연결 코드 생성”을 눌러 12자리 코드를 iPhone PWA에 입력한다.
7. Galaxy에서 “연결 상태 확인”을 누르고 iPhone의 승인 코드와 비교한 뒤 승인한다.
8. iPhone PWA에서 새로고침한 뒤 “알림 활성화”를 누른다.
9. Galaxy에서 “테스트 이벤트 보내기”로 확인한다.

## 문자·전화 권한

문자는 기본적으로 Notification Listener로 전달한다. 직접 SMS 수신이 꼭 필요한 경우에만 RECEIVE_SMS를 요청하고 “SMS 직접 수신”을 활성화한다. RECEIVE_SMS는 OS·설치자에 따라 제한될 수 있다. 허용되지 않으면 문자 앱 알림 방식으로 사용한다. READ_SMS나 기본 SMS 앱 변경을 요구하지 않는다. 직접 수신 모드에서는 문자 앱 알림을 제외해 중복을 줄인다. 직접 SMS에는 원본 notificationKey가 없어 열기·삭제 버튼이 없다.

전화 상태는 **전화 상태 권한**을 허용하고 **전화 상태 전달**을 켠다. 수신·통화 연결·종료를 전달한다. 발신자를 표시하려면 새 **발신자 번호·연락처 권한 (선택)** 버튼에서 READ_PHONE_STATE/READ_CALL_LOG/READ_CONTACTS를 허용한다. 통화 기록 목록을 읽지 않고 OS의 수신 번호와 번호에 해당하는 연락처 이름 한 건만 조회한다. 통화 알림의 Person/제목 정보도 보강에 사용한다. 번호 없는 broadcast와 번호 있는 broadcast가 중복 도착해도 같은 callId를 유지한다.

READ_CALL_LOG는 설치 경로/OS 정책의 제한 권한이어서 허용할 수 없거나 번호를 제공받지 못할 수 있다. 이때 전화 앱 알림에 제공된 발신자를 활용하며, 정보가 없으면 일반 수신 알림을 표시한다. 주소록 전체를 서버로 보내지 않는다. 번호 비공개·통화 대기·Dual SIM·VoIP 앱은 별도 기기 확인이 필요하며 셀룰러 통화 하나의 best-effort 상태를 관리한다. 통화 오디오, 원격 전화 받기/거절, CallKit은 구현 대상이 아니다.

받거나 거절하거나 종료하면 본문 없는 통화 상태만 서버에 전송한다. iPhone PWA가 열려 있으면 수신 배너를 닫고 기존 통화 기록을 갱신한다. 완전히 닫힌 iPhone에서 즉시 닫기는 보장되지 않는다. 자세한 이유는 [iPhone 가이드](iphone-setup.md)에 있다.

기존 설치는 같은 서명의 **최신 APK를 덮어 설치**한다. 앱 삭제/데이터 초기화는 연결·키를 지우므로 업데이트 시 필요하지 않다. E2EE 사용 중 삭제/요청 결과 배너까지 없애려면 Android 앱도 업데이트해야 한다.

## 배터리·연결 상태

이벤트 발생 시 즉시 HTTPS 전달을 시도하고 실패하면 Room Queue + WorkManager로 재시도한다. WorkManager의 실행 시간은 Doze·절전 정책에 의해 지연될 수 있다. One UI에서 앱을 “절전 상태 앱/초절전 상태 앱”으로 분류하지 않도록 설정한다. 강제 종료(Force stop)한 앱은 직접 다시 열기 전까지 이벤트·FCM이 동작하지 않을 수 있다. 상시 Foreground Service나 WebSocket을 사용하지 않는다.

OPEN이 백그라운드에서 들어오면 “iPhone에서 열기 요청” 알림을 Galaxy에서 누른다. 잠금 화면을 우회하지 않는다. 원본 알림이 사라지면 열 수 없다고 표시한다. Dismiss/ACK는 Notification만 제거하며 카카오톡/Discord/Instagram의 서버 읽음 상태를 바꾸지 않는다.

Queue는 영속 Room + 단일 WorkManager 작업으로 복구한다. 앱 프로세스가 살아 있을 때는 한 개의 재시도 타이머를 보조로 사용한다. idle polling/Foreground Service가 아니며 프로세스 종료·Doze 중 정시 실행을 보장하지 않는다. 기본 전달 성공 후 E2EE 키를 생성·직접 iPhone에 입력하고 양쪽 지문을 비교하는 방법은 [보안 문서](security.md#e2ee-설정)를 따른다.

## 알림 답장 업데이트

APK 1.2.0 / versionCode 3을 기존 앱 위에 설치한다. 기존 Release와 동일 서명이며 연결을 유지한다. Android 12 이상에서 원본 Notification의 RemoteInput 자유 입력 답장을 PWA에 제공한다. 별도 SMS…5612 tokens truncated… 알림 전달 → iPhone 오프라인일 때만 전달할 앱에서 선택한다. iPhone 네트워크 경로가 없고 BLE 연결이 살아 있을 때만 추가 수신한다. [자세한 기준과 시험](offline-apps.md).
