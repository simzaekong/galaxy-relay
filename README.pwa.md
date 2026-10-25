> 공개 스냅샷 안내: 과거 배포·빌드 기록입니다. 운영 주소와 다운로드는 제공하지 않으며, 현재 상태와 설치 전제는 [공개 설정 안내](docs/public-source.md)를 먼저 확인하세요.

# Galaxy ↔ iPhone Relay

Galaxy 알림을 iPhone 홈 화면 PWA로 전달하는 개인용 시스템이다. 새로운 Android Studio **Java** 프로젝트, Cloudflare Workers + KV Backend, VAPID Web Push, Firebase HTTP v1 명령 채널, PWA/IndexedDB를 포함한다. 네이티브 iOS 앱이나 Apple Developer Program은 필요하지 않다.

**현재 배포 주소:** [Galaxy Relay PWA](https://relay.example.com)

**알림 답장 (2026-10-09):** PWA에서 최신 알림을 눌러 상세를 열고 **답장 입력 → 답장 보내기**를 누른다. Galaxy의 원본 알림이 자유 입력 `RemoteInput` 답장 동작을 제공할 때만 입력창을 표시한다. Android 12 이상, 활성 원본 알림, Galaxy 잠금 해제가 필요하다. **APK 1.2.0 / versionCode 3으로 업데이트**하고 새로 받은 알림으로 시험한다. 기존 연결은 유지한다. E2EE 사용자는 예전 수신 전용 키를 설정에서 **같은 키로 다시 저장**하면 암호화 답장을 사용할 수 있다. [사용 방법·제한·보안](docs/reply.md).

**새 알림함 디자인 (2026-10-10):** Linear·Notion 알림함을 참고해 밝은 바탕, 정돈된 카드와 보라색 강조로 재구성했다. 검색·분류를 항상 표시하고 날짜별 정리, 묶음 개수, 모바일 하단 상세/답장, 라이트·다크 모드를 제공한다. 큰 시계·배경·유리 렌더러는 현재 앱에서 제외한다. PWA 닫기/재실행으로 갱신하며 이번 디자인 변경에는 APK 재설치가 필요 없다. [디자인·미리보기·레퍼런스](docs/inbox-design.md).

**최신 조작 방식:** 앱·발신자/채팅 제목별로 쌓인 묶음을 누르면 모든 알림을 펼친다. 개별 알림을 누르면 상세를 표시하고 읽음 처리하며 기록과 본문은 유지한다. 이어서 표시된 상세 카드를 한 번 더 누르면 Galaxy에서 원본 알림 열기를 요청한다. 왼쪽으로 충분히 밀면 개별/묶음의 기록과 연결된 Galaxy 알림을 지운다. 별도 열기·삭제 버튼과 상세 자동 ACK 옵션은 제거했다. 묶음 펼치기만으로는 기록을 지우지 않는다. 원본 Galaxy 알림 제거가 전달되면 관련 PWA 기록도 삭제한다. 2026-10-09 답장 기능은 Galaxy APK 1.2.0 업데이트와 PWA 갱신이 필요하다.

**이전 화면 이력 · 2026-10-04 PWA 디자인·기록 업데이트:** 제공한 배경, 실제 날짜·시계, 읽지 않은 알림 수와 유리 카드 화면을 적용했다. 검색·분류는 펼쳐 사용할 수 있다. 상세 조회는 읽음 표시만 하며 본문을 IndexedDB에 유지하여 닫거나 새로고침한 뒤에도 다시 볼 수 있다. 읽음 알림은 읽지 않은 개수에서 제외된다. 기록 삭제는 왼쪽 밀기, Galaxy 원본 제거 동기화, 전체 기록 삭제 및 5,000개 상한에서만 수행한다. 상세 조회 시 자동 ACK는 보내지 않는다. 이전 버전에서 이미 삭제한 본문은 서버에 저장하지 않아 복원할 수 없다. iPhone PWA를 인터넷 연결 상태에서 닫았다 다시 열어 갱신한다. Android APK 재설치는 필요 없다. 화면 미리보기 (개발 기록: 공개본에는 파일 미포함).

**2026-10-04 사용자 확인:** 삼성 기기 두 대에서 정상 동작했다는 보고와 실제 iPhone Push 수신 사진을 받았다. 개별 인수 시나리오와 OS 버전은 아직 미기록이다. [오늘 기록·다음 작업](docs/work-log.md)에 이어서 기록한다.

Backend 독립 테스트 → 신규 Android 앱 등록/FCM 검증 → 알림 기능 → PWA → 실제 클라우드 통합 테스트 → 보안/E2EE 순서로 개발했다. 실제 배포된 Worker, FCM, Chrome Web Push와 Android 15 에뮬레이터의 연결을 테스트했다. 물리 기기의 앱별 동작과 새 버전의 iPhone 통화 알림 종료는 별도 인수 검증이 필요하다. 구현·테스트된 부분과 실제 기기에서 확인할 부분은 [테스트 결과](docs/test-results.md)에 구분한다.

**알림·전화 개선 (1.1.0부터):** 기존 앱을 삭제하지 않고 새 APK (개발 기록: 공개본에는 파일 미포함)를 덮어 설치한다. 같은 Release 키를 유지했다. iPhone PWA는 인터넷 연결 상태에서 닫았다가 다시 열어 업데이트한다. 제목은 `김철수 · 카카오톡` 형태로 표시하며, 삭제·요청·통화 종료 상태는 새 Push 배너를 만들지 않는다. 전화 설정에서 **발신자 번호·연락처 권한 (선택)**을 허용하면 번호에 해당하는 이름을 조회한다. 허용하지 않거나 OS가 번호를 숨기면 전화 앱 알림의 발신자 정보를 우선 활용하고, 정보가 없으면 일반 수신 알림을 표시한다.

## 지금 자신의 기기에 설치

이 작업에서 사용자 계정에 별도 Cloudflare Worker/KV와 무료 Firebase 프로젝트를 생성하고 설정했다. 기존 프로젝트를 수정하지 않았다.

1. 설치용 Release APK (개발 기록: 공개본에는 파일 미포함)를 Galaxy에 설치한다. 이번 프로젝트 전용 로컬 키로 서명했으며 debuggable은 꺼져 있다. Debug APK (개발 기록: 공개본에는 파일 미포함)는 개발·계측 시험용이다.
2. Galaxy Relay의 Backend 주소가 위 URL인지 확인한다. 설치 비밀값은 이 로컬 repository의 Git 제외 파일 `.secrets/cloudflare.json`의 `INSTALL_SECRET`을 직접 확인해 입력한다. 이 파일을 공유하거나 Git에 추가하지 않는다.
3. **Backend 등록**을 누른다. **알림 접근 허용**, **Relay 알림 표시 허용**을 설정한다. 기본 앱 전달 목록과 필요한 전화/SMS 선택 권한은 [Android 가이드](docs/android-setup.md)를 따른다.
4. iPhone Safari에서 위 URL → 공유 → **홈 화면에 추가** → 홈 화면의 Relay 앱을 연다. iOS 16.4 이상이 필요하다.
5. Galaxy에서 **iPhone 연결 코드 생성** → PWA에 12자리 코드 입력. Galaxy의 **연결 상태 확인**에서 양쪽 8자리 승인 코드를 비교하고 승인한다.
6. PWA를 새로고침하고 설정의 **알림 활성화**를 직접 누른다. Galaxy의 **테스트 이벤트 보내기**로 확인한다.
7. 기본 전달을 확인한 뒤 원하는 경우 [E2EE 설정](docs/security.md#e2ee-설정)을 진행한다. 기본값은 HTTPS + Token이고 E2EE는 사용자가 양쪽 키를 맞춘 뒤 켜는 선택 기능이다.

`google-services.json`과 실제 비밀 파일은 이 설치의 로컬에만 있으며 source ZIP에는 포함하지 않는다. APK에는 Firebase Android 공개 client configuration이 포함되지만 VAPID/FCM service account private key, INSTALL_SECRET, device token, E2EE key는 포함되지 않는다. 생성 APK는 본인 계정의 이번 Firebase 프로젝트를 사용한다.

## 기능

- 일반 앱/KakaoTalk/Discord/Instagram/삼성·Google 메시지 알림 및 사용자가 선택한 앱 전달
- 선택형 직접 SMS 수신, 전화 ringing/offhook/ended 이벤트. 선택형 번호·연락처 권한과 원본 전화 알림으로 발신자를 보강하고 같은 통화 배너를 갱신한다. 연결·거절·종료 시 PWA가 상태를 조회해 기존 수신 배너를 닫는다.
- PWA 알림 목록·검색·전체/전화/문자/앱 분류·상세·오프라인 조회, 최근 5,000개 IndexedDB 보관
- **PWA 답장**: 원본 알림의 RemoteInput을 통해 원본 앱으로 전송. 최대 500자, 원격 답장 결과 조회, 중복 명령 차단, 선택형 답장 E2EE. 실제 상대방 수신·읽음 상태는 판정하지 않는다.
- **폰에서 열기**: 활성 원본 Notification의 `contentIntent`만 사용. Relay 화면이 보이고 잠금 해제 상태이면 직접 실행 시도, 백그라운드에서는 Galaxy 확인 알림으로 안전하게 넘긴다.
- **왼쪽 밀기**로 PWA 기록·Galaxy 알림 삭제, **상세 카드 재탭**으로 원본 열기 요청, Galaxy 원본 제거를 PWA 기록 삭제로 best-effort 동기화
- Room 영속 Queue, 5초/15초/30초/1분/5분 재시도, TTL·최대 20회·최대 5,000개 대기 상한
- 원본 알림 갱신은 새 UUID로 이전 본문을 보존하며, 갱신되거나 제거된 이전 기록의 원격 조작을 막는다.
- Android Keystore로 credential/로컬 이벤트 암호화, 서명된 FCM 명령·시간/nonce/명령 중복 방지
- 선택형 AES-256-GCM 알림·답장 E2EE와 Web Crypto. 키를 서버에 전달하지 않는 직접 키 전달 및 지문 확인
- 연결 해제, PWA 기록 삭제, 본문·이름·Push 미리보기 숨기기, Dark Mode 및 safe area 처리

Android 알림을 제거하는 동작은 원본 앱 서버의 읽음 상태 변경과 다르다. 통화 오디오, CallKit, iMessage, 앱 내부 읽음 강제 변경, 보안 잠금 우회는 구현하지 않는다.

## Repository

```text
galaxy-relay/
  backend/            Worker TypeScript, API/transport tests, Wrangler, 설치 scripts
  android/            신규 Android Studio Java 프로젝트, Gradle Wrapper
    app/              개인용 실제 Relay 앱
    test-fixture/     통합 테스트 전용 원본 알림 앱 (Relay APK에 포함 안 됨)
  web/                PWA source/public/Service Worker/manifest, browser tests
  docs/               설치·구조·보안·장애·검증 문서
  artifacts/          생성 APK, 비밀·빌드 결과를 제외한 source ZIP, SHA-256
  .secrets/           이 설치의 로컬 비밀 (Git/ZIP 제외)
  README.md
```

## 다른 계정에 재설치·빌드

Node.js 22+, JDK 17 이상(검증 환경 JDK 21), Android SDK 36 및 Android Studio를 준비한다. Java minSdk 26 / targetSdk 36이다. Kotlin 소스는 없다. Gradle Wrapper 9.4.1 / AGP 9.2.1, npm lockfiles를 포함한다.

1. [Cloudflare Backend 설치](docs/backend-setup.md): 무료 계정·KV binding·VAPID·secrets·HTTPS/custom domain·배포
2. [Firebase 설치](docs/firebase-setup.md): 무료 프로젝트·Android client config·FCM v1·전용 서비스 계정
3. [Android 설치](docs/android-setup.md): 새 프로젝트 열기·APK 빌드·권한·배터리·연결
4. [iPhone 설치](docs/iphone-setup.md): Safari 설치·직접 알림 허용·페어링·오프라인
5. [보안](docs/security.md), [문제 해결](docs/troubleshooting.md), [구조](docs/architecture.md)

```sh
cd backend
npm ci
npm run check
npm test
# Cloudflare/Firebase 설정은 가이드 순서로 준비
cd ../android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
cd ../web
npm ci
npm test
npm run build
cd ../backend
npm run deploy
```

Windows에서는 `gradlew.bat`, `npm.cmd`, `npx.cmd`를 사용할 수 있다. 프로젝트에 포함하지 않는 `android/local.properties`에 로컬 Android SDK 경로를 설정한다.

PWA browser 테스트는 Backend의 `npm run dev`로 8787 서버를 실행한 뒤 `web`에서 `npx playwright install chromium` 및 `npm run test:browser`를 실행한다. 실제 FCM/Push 계측 테스트는 별도 테스트 emulator와 계정 설정이 필요하다. 재현 방법은 테스트 결과 문서에 있다.

## 운영비와 필수 한계

Workers Free + KV Free + FCM + Web Push + 로컬 IndexedDB로 무료 한도 안의 **월 0원**을 목표로 한다. 결제 계정·VPS·유료 database를 추가하지 않았다. Custom Domain 없이 workers.dev로 사용할 수 있다. 공격·한도 초과에는 가용성을 보장하지 않는다.

KV는 eventual consistency이고 원자적 compare-and-swap이 없다. 전 지역에서의 엄밀한 1회 코드 claim/즉시 token revoke/전역 rate limit은 KV만으로 보장할 수 없다. 12자리 코드, 5분 만료, 지역 rate limit, Galaxy 최종 대면 승인, 상호 peer 검사, receiver 영속 중복 방지로 대응했다. 이 선택과 비용·안정성 절충은 [보안 문서](docs/security.md)에 설명한다.

Push 수락은 실제 iPhone 표시 성공이 아니다. 알림 본문 서버 저장/백필을 하지 않으므로 Push TTL·iOS 저장 공간 정책·긴 오프라인 후 놓친 기록을 서버에서 복원할 수 없다. 상태는 ID·종류·시간·결과만 KV에 임시 보관하고 PWA가 보일 때 조회한다. 전화 상태는 24시간, 삭제·명령 결과는 1시간 후 만료된다. 본문·제목·번호·이름은 보관하지 않는다. 조회도 무료 KV read 한도를 사용하며 일반 조회는 15초, 통화·명령 대기 중에는 5초 간격이다.

**iPhone 통화 배너 한계:** PWA가 열려 있으면 통화 연결·거절·종료 상태를 조회하고 기존 알림을 닫는다. 다시 열거나 다음 실제 메시지 Push를 받을 때도 정리한다. PWA가 완전히 닫힌 동안 즉시 지우는 동작은 보장할 수 없다. [WebKit은 사용자에게 표시하지 않는 Push를 허용하지 않는다](https://webkit.org/blog/16535/meet-declarative-web-push/). 새 종료 배너를 만들지 않으면서 닫힌 PWA를 항상 깨울 수는 없으므로 이 방식으로 선택했다. KV 전파·네트워크·OS 알림 API에 따라 갱신은 지연될 수 있다. 최신 Android의 자동 Activity 실행, 앱별 contentIntent, One UI 절전, 잠금 상태는 OS/앱의 제약을 따른다.

프로젝트 내부 `sources/` 참조 자료는 변경하지 않았다. 프로젝트 자원·비밀은 개인 계정에서 관리하고 공개 source ZIP과 분리한다.
