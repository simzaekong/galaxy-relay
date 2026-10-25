# 실행·검증 결과

## 2026-10-10 새 알림함 디자인

배포 version: 12a449ca-fcdf-4c96-b493-13489a514241. 공개 /api/status HTTP 200. HTML/CSS/app/Service Worker/manifest 다섯 파일의 배포 SHA-256이 최종 빌드와 일치했다. 실제 배포 URL에서 새 격리 Chrome context에 합성 알림을 넣어 라이트/다크/상세 답장/설정 미리보기를 생성하고 직접 확인했다. UI 시험의 API 응답과 명령 결과는 모의이며 실제 사용자 기기 또는 알림은 사용하지 않았다.

- PWA Node 12개 PASS. 기존 모델·암호화·알림 표시·묶음 유지.
- Chrome 전체 29개 PASS: 일반/SMS/전화 필터, 검색, 안전한 본문 표시, 오늘/어제 구분, 읽음과 기록 보존, 앱/채팅 묶음 펼치기, swipe 개별/묶음 삭제, 원본 제거 동기화, 상세 재탭 OPEN, 일반/E2EE 답장과 중복 전송 방지, 실제 SW Push 저장/표시, 통화 종료 배너 회수, 5,000개 상한, 오프라인 조회.
- 320/390/430/1024px 및 라이트/다크에서 본문 화면 넘침·상세 화면 경계·Escape 닫기·키보드 설정 스위치와 저장 유지 PASS.
- 최초 전체 실행은 상세 open 비동기 작업 완료 전에 boundingBox를 읽던 새 레이아웃 시험 1개가 실패했다. 화면 표시를 기다린 뒤 최종 전체 29개를 통과했다.
- Windows WebKit 새 디자인 시험 2개 PASS(온라인 화면/검색/날짜/읽음, 라이트·다크/320~1024px 상세/키보드 설정). 오프라인 reload 1개는 기존과 같은 WebKit encountered an internal error로 실패하여 전체 WebKit 성공으로 기록하지 않는다.
- 큰 시계·배경·광학 renderer를 production import/build/static cache에서 제외했다. 구버전 캐시만 새 캐시로 교체하고 IndexedDB 기록·인증·E2EE 키의 저장 형식은 유지했다.
- 새 디자인에 대한 물리 Galaxy/iPhone·실제 FCM 답장 재검증은 하지 않았다. Android와 Backend 기능 코드는 변경하지 않았다. APK 1.2.0을 유지하며 PWA 닫기/재실행으로 업데이트한다.

[디자인·참고 자료·미리보기](inbox-design.md).

## 2026-10-09 PWA 답장

Backend/Android/PWA 순서로 답장 기능을 구현했다. 배포 version: `515dc14f-e678-4884-a466-914d46b8ab30`. 공개 health HTTP 200, HTML/CSS/app/API/crypto/model/DB/SW 8개 파일의 배포 SHA-256이 최종 PWA 빌드와 일치했다. Android **1.2.0 / versionCode 3** Debug/Release build, Java 단위 4개, lintDebug/lintRelease(오류 0), 최종 RemoteInput 계측 2개 PASS. 이전 Release와 같은 RSA 3072-bit 서명 인증서/v2 signature를 확인했다. 전용 에뮬레이터에서 기존 1.1.0 Release 설치 → 1.2.0 `install -r` 업데이트 → MainActivity 실행·versionCode 3 확인도 통과했다.

- Backend 독립 mock 19개 PASS: 역할·인증·pair·시각·rate limit·입력 크기, plain/encrypted REPLY union, 1-byte 답장 암호문 허용/짧은 암호문 거부, 전체 reply HMAC, 서버 미보관, 조용한 결과 receipt 포함.
- PWA Node 12개 PASS. 전체 Chrome 회귀 30개 PASS 후 최종 정리된 reply spec 5개도 별도 재실행해 모두 PASS했다. 평문/암호문 전송, authenticated target metadata, 중복 submit·재열기·reload 대기, 지원 없음/오프라인, legacy decrypt-only 키의 평문 downgrade 거부, 본문 기록 유지 포함. 원격 결과는 이 단위 UI 시험에서는 mock이다.
- **실제 클라우드 통합 PASS:** 전용 API35 emulator(5580) + 별도 fixture 앱 + 실제 배포 PWA/Chrome. 원본 알림의 reply capability → NLS/Room → HTTPS Worker → 실제 Web Push → IndexedDB → PWA 답장 form → Worker → 실제 Firebase HTTP v1 data message → Galaxy active Notification의 RemoteInput PendingIntent → fixture private storage에서 합성 답장 본문 일치 확인 → `/api/state` → PWA 조용한 실행 상태. 평문과 AES-GCM 양쪽 모두 통과했다.
- 동일 commandId/nonce 요청을 실제 Worker/FCM 채널로 다시 전송하고 fixture 수신 카운트가 늘지 않는지 확인했다. 본문/원래 알림 기록은 전송 후 유지됐다. ‘원본 앱 전달’은 실제 상대방 수신 확인으로 처리하지 않았다.
- 시험용 Pair는 `/api/unpair` 성공으로 폐기했다. 개인 사용자 Pair를 건드리지 않았다. 시험 본문은 별도 fixture 앱 내부에만 기록하며 실제 메신저나 연락처로 메시지를 보내지 않았다. `artifacts/pwa-reply-preview.png`는 이 합성 fixture의 실제 배포 상세 화면이다.

초기 통합 시도에는 emulator DNS 오류, 초기 NLS 재연결 시 알림 누락, 원격 reply 미확인, Chrome Push 등록 지연이 있었다. 활성 알림 재수집 및 postTime 확인을 추가하고 process-dependent PendingIntent.hashCode를 액션 지문에서 제거했다. 최종 실행은 기존 시험 Chrome subscription profile을 재사용하고 양쪽 KV pair/push 전파를 기다렸으며 시험 Galaxy의 잠금 해제를 명시했다. 최종 평문·E2EE 왕복과 중복 억제가 모두 통과한 실행 결과로만 위 PASS를 기록한다. 실패한 초기 실행들도 시험 Pair를 폐기했다.

**Windows WebKit 추가 시험은 PASS로 처리하지 않는다.** 최초 5개 중 지원 없음/오프라인·legacy 키 거부 2개만 통과하고 전송 3개는 POST mock capture 실패였다. 별도 window fetch 모의 구성에서도 pointer 안정성/키보드 상세 표시 대기가 timeout으로 끝났다. 해당 시험 방식 변경은 최종 Chrome spec에서 제거했다. 물리 iPhone Safari/Web Push/키보드와 실제 카카오톡·문자 앱 답장 인수 시험은 미검증이다. 기존 Windows WebKit offline reload 한계도 그대로다.

재현: 로컬 Worker/PWA 서버를 켠 상태에서 `cd web && npx playwright test test/browser/reply.spec.js --workers=1`. 전용 emulator에 최종 Debug/test APK와 test-fixture APK를 설치하고 NLS/fixture 알림 권한을 허용한다. `backend/scripts/emulator-config.mjs` → `RuntimeTest` → `backend/scripts/export-test-link.mjs` → `web/scripts/reply-live.mjs`. 스크립트는 실제 클라우드 시험 Pair를 생성/사용하므로 **개인 실제 기기가 아닌 전용 emulator-5580에만** 실행한다. 기존 시험 profile은 `--profile=reply-chrome-...`로 선택할 수 있다. [사용·지원 범위](reply.md).

## 2026-10-04 리퀴드 글래스

Chrome 배포 미리보기는 완료했다. 로컬 WebKit 미리보기도 생성해 굴절 화면을 확인했으나, 추가 배포 URL의 WebKit 미리보기 실행은 출력 없이 지연되어 종료했다. 이를 배포 WebKit 검증 성공으로 기록하지 않는다. WebKit artifact는 앞선 로컬 실행 결과이며 배포 URL의 최신 화면 증거로 사용하지 않는다.

배포 완료 version: `44e2be2c-bba7-45eb-b313-c26576d1cc0d`. 변경된 CSS/app/glass/SW 4개 파일의 배포 SHA-256이 로컬 최종 빌드와 일치하고 공개 API health HTTP 200을 확인했다. 실제 배포 파일을 새 브라우저 context에서 불러와 합성 fixture로 광학 화면·묶음·상세를 렌더링했다. 미리보기의 API는 브라우저 내 모의이며 실제 사용자 기록·FCM은 사용하지 않는다. PWA 닫기/재실행으로 갱신하며 APK 재설치는 필요 없다.

Chrome Playwright 전체 25개 PASS. 광학 캔버스의 실제 유색 픽셀·투명한 둥근 모서리, 선명한 DOM 상세와 읽음 기록 유지, 스크롤 시 샘플 좌표 변경, 정지 시 draw 중단, 최대 16개 및 화면 밖 캔버스 해제, WebGL 미지원 시 상세/기록 유지, shader·배경 offline cache 및 오프라인 재실행을 검증했다. 기존 스와이프·묶음·열기 HTTP 요청·상태 삭제·입력 안전·전화 Push·crypto 모델·5,000개 상한 회귀를 포함한다.

Windows Playwright WebKit 26.6: 온라인 GPU 픽셀/상세·스크롤/메모리 해제·WebGL 미지원 대체 표시 3개 PASS. `context.setOffline(true)` 뒤 `page.reload`가 엔진 내부 탐색 오류로 실패하는 결과가 반복되어 오프라인 새로고침 시험 1개는 미검증이며 `npm run test:webkit`는 현재 비정상 종료한다. 실제 iOS Safari/PWA의 동일 문제 여부는 판단하지 않았으며 실기기 시험이 필요하다. 알려진 오프라인에서 캐시를 먼저 제공해도 해당 테스트 환경 오류는 유지됐다. 성공한 Chrome offline 시험으로 WebKit 결과를 대신하지 않는다.

초기 Chrome GPU 시험은 DOM 목록 교체로 해제된 이전 캔버스의 픽셀을 읽던 시험 경합을 연결 여부·유효 픽셀의 한 스냅샷으로 기다리게 수정했다. 초기 swipe 좌표가 연결 폼 정리 이전 레이아웃에서 잡히는 경우는 첫 렌더부터 저장된 연결을 적용하고 시험도 초기화를 기다리도록 보완했다. 상태 순회 fixture와 실제 token 거부가 섞이던 HTTP 경로도 격리했다. 최종 전체 25개를 통과했다. 원격 FCM 결과는 모의이며 Android/Backend 소스·APK는 변경하지 않았다. 물리 iPhone의 시각 효과·배터리·GPU 성능은 미검증이다.

## 2026-10-04 상세 조회 기록 유지

배포 완료 version: `039cdaa6-f9a2-495d-9608-d44d43c5be1c`. 변경된 HTML/app/DB/SW 4개 파일의 배포 SHA-256이 로컬 최종 빌드와 일치하고 공개 API health HTTP 200을 확인했다. 배포 URL에서 새 browser context와 합성 fixture로 실제 화면을 렌더링하며 상세 조회 후 7개 기록 유지 및 읽음 1개를 확인했다. 해당 API 응답은 브라우저 내부 모의다. APK 재설치는 필요 없다. 소스 ZIP·무결성 파일을 갱신하고 실제 secret 제외 검사를 적용했다.

Node 10개 / Playwright 21개 PASS. 상세 조회가 본문을 유지하고 읽지 않은 개수만 낮추는지, 닫기·재열기·새로고침·동일 이벤트 재전송 후에도 읽음 상태와 본문을 유지하는지, 새로운 이벤트는 읽지 않음으로 표시하는지, 오프라인 재조회와 기존 read 행 이관, 상세 스와이프 DISMISS 및 조용한 제거 receipt가 남아 있는 기록을 삭제하는지를 확인했다. 기존 Push·통화·crypto·묶음·제스처·입력 안전 렌더링·5,000개 상한도 통과했다. 원격 명령의 HTTP/FCM 결과는 모의이며 새 물리 Galaxy/iPhone 동작 검증으로 처리하지 않는다. 아래 상세 조회 삭제 기록은 이전 버전 이력이다.

## 2026-10-04 제스처·묶음 알림

배포 완료 version: `1b8a7b21-bc73-480b-9318-89f7dc057248`. 공개 API health HTTP 200 및 최종 HTML/CSS/app/DB/state-sync/groups/gestures/SW 8개 파일의 배포 SHA-256이 로컬 빌드와 일치했다. 배포 URL의 실제 정적 파일을 새 browser context에서 열고 합성 fixture로 light/dark/묶음 펼침/상세 화면을 다시 렌더링했다. 해당 미리보기의 API 응답은 브라우저 내부 모의이며 새 물리 기기 FCM/Web Push 전달 시험으로 처리하지 않는다.

Node 10개 / Playwright 21개 PASS. 앱·대화 그룹 격리와 순서, 스택 펼치기만으로 읽음/명령 발생 금지, 첫 상세의 로컬 consume 및 ACK 금지, 재탭 OPEN HTTP 요청, 묶음/개별 실제 좌측 pointer swipe로 DISMISS 요청 및 로컬 삭제, 짧은 밀기/세로 움직임 오작동 방지, offline/FCM 502 시 유지, 상세에서 지운 최신 이벤트의 제거 receipt로 이전 갱신 기록까지 정리, 늦은 갱신 복원 방지, 45개 이벤트의 상태 순회, 기존 read/removed 행 이관을 확인했다. 기존 Service Worker Push·발신자·통화 배너 조용한 정리·5,000개 보관·암호화 모델·offline cache·입력 안전 렌더링 회귀도 포함한다.

초기 전체 시험에서 pointer 동작 도중 상태 조회 완료가 카드 DOM을 교체하는 경합을 확인해 목록 읽기 전/후 모두 active gesture를 검사하고 렌더링을 미뤘다. 좌표 시험 helper도 일시적으로 교체 중인 node의 실제 bounding box를 기다리게 보완했다. 이후 전체 21개가 통과했다. light/dark/묶음 펼침/상세 화면은 합성 fixture를 사용해 실제 브라우저로 렌더링하고 확인했다. 명령 테스트의 HTTP/FCM 결과는 모의이며 물리 Galaxy에서 새 제스처로 원본 Activity 실행을 검증한 결과는 아니다. Android/Backend 코드는 변경하지 않았다. 실제 OS 제한에 따른 Galaxy 사용자 확인 fallback과 receipt TTL에 따른 동기화 한계는 그대로다.

## 2026-10-04 PWA 유리 화면·확인 기록 삭제

Node 8개 / Playwright 브라우저 13개 PASS. 기존 Push·전화 배너 정리·ACK·원격 명령·암호화 모델을 포함하고, 상세를 실제로 열 때의 IndexedDB 본문 삭제, 내용 없는 tombstone, 동일 이벤트 재전송/상태 갱신으로 기록 복원 금지, 새 UUID 메시지 정상 표시, 기록을 지운 상세의 원격 삭제 응답, 오프라인 상세 삭제, 배경의 Cache API 저장, 320/390/430px 가로 넘침을 검증했다. 실제 브라우저에서 합성 로컬 데이터로 light/dark/detail 화면을 캡처해 확인했다. Android/Backend 코드는 변경하지 않았으며 APK 빌드나 물리 iPhone 새 UI 시험을 했다고 처리하지 않는다.

재현: 로컬 Worker를 8787에서 실행하고 `cd web`, `npm run build`, `npm test`, `npm run test:browser`. `node scripts/preview.mjs`는 실제 사용자 기록 없이 합성 fixture만 로컬 browser context에 만들어 미리보기를 출력한다.

배포 완료 version: `0bfd6d23-9ec9-446e-adbd-3651b13e553c`. 배포 주소에서도 새 browser context와 합성 fixture로 화면 렌더링 및 상세를 열면 7개→6개로 목록이 줄어드는 동작을 확인했다. 공개 API health HTTP 200, 최종 HTML/CSS/app/DB/state-sync/SW/배경 7개 파일의 배포 SHA-256이 로컬 최종 빌드와 일치했다. 합성 fixture 시험에서 API 응답은 브라우저 내부 모의이며, 새로운 실기기 FCM/Web Push 전송을 검증했다고 처리하지 않는다. 소스 ZIP 및 무결성 파일을 갱신하며 실제 secret 제외 검사도 수행한다.

## 2026-10-04 · 1.1.0 추가 검증

| 시험 | 결과 |
|---|---|
| Backend TypeScript / Vitest 17개 | PASS. 조용한 receipt, input allowlist, paired namespace 격리, 통화 최신 상태, unpair 정리, 인증된 Push fingerprint 포함 |
| PWA Node 8개 / Playwright 10개 | PASS. 발신자·앱 제목, privacy, 실제 Service Worker 전화 배너 생성·offhook 후 close, ended 갱신, 무배너 명령 결과, 늦은 수신 tombstone, ACK/오프라인 회귀 |
| Android debug/release 빌드 / JVM 2개 / lintDebug·lintRelease | PASS. min26/target36, Java, 오류 없음. 플랫폼 deprecation warning은 남음 |
| 서명 Release APK 1.1.0 설치·실행 | PASS. 최종 export APK를 API35 전용 에뮬레이터에 설치해 MainActivity COLD launch 성공. versionCode 2/min26/target36 확인 및 run-as 거부로 non-debuggable 확인 |
| Android PhoneLifecycleTest 2개 | PASS. 연락처 한 번호 조회·미일치, 중복 번호 보강, 같은 callId, offhook·ended 최소 상태 및 로컬 번호 정리 |
| RuntimeTest 2개 | PASS. 실제 Firebase 등록·배포 Backend 페어링 및 Room/Keystore/replay |
| Java→Web Crypto | PASS. 변조·wrong-key·downgrade 거부 |
| 실제 FCM OPEN 직접/fallback·DISMISS·ACK·중복 / 오프라인 큐 | 앞선 Android 계측 PASS. 최종 전체 재시험은 OPEN 직접 실행 시 원본 Activity 전환 assertion 실패. Relay focus=true여도 원본 화면 이동을 보장하지 않으며 해당 최종 재시험의 후속 단계는 완료로 처리하지 않음 |
| 실제 가상 모뎀 SMS·발신 번호·수신·종료 → 로컬 Room | PASS. 정상 앱 프로세스에서 실제 protected broadcast 수신. 번호 있는/없는 중복 수신 및 종료 기록 검증. receiver를 직접 호출하는 시험과 구분 |
| 최신 Push 구독의 Android edge 반영 | PASS. endpoint 원문 없이 SHA-256 fingerprint 일치 확인 |
| 실제 cloud Web Push → 이번 Chrome IndexedDB | PASS. 실제 Android AES-GCM payload → 배포 Worker → 실제 Chrome Push → IndexedDB 복호화 저장을 명시적 Node await로 확인. provider 접수만으로 성공 판정하지 않음 |
| 실제 가상 모뎀 → Cloud Push 전화 배너·조용한 종료·배너 닫기 | PASS. 번호 5551234가 있는 실제 ringing Push와 SMS를 복호화 저장. 종료는 receipt 조회로 같은 기록을 ended로 갱신하고 기존 전화 배너만 닫음. 새 종료 Push 배너 없음. 물리 iPhone 완전 종료 상태의 즉시 닫기를 검증한 결과는 아님 |

초기 에뮬레이터 Firebase `FIS_AUTH_ERROR`는 전용 시험 앱의 오래된 설치 상태를 초기화한 뒤 정상 재발급·페어링으로 해결했다. 사용자 기기는 초기화하지 않았다. 초기 ACK 시험의 고정 1.5초 대기는 KV 전파를 보장하지 않아 설정이 실제 조회될 때까지 기다리도록 수정했다. 최초 SW 설치와 배너 표시 완료 전의 브라우저 assertion도 완료 조건을 기다리도록 보완했다. 실제 인수 검증을 모의 시험으로 대신하지 않는다.

이번 통화 시험의 초기 실패는 수신기 작업이 완료되기 전에 가상 통화를 취소하거나 계측을 재시작하는 시험 타이밍을 보완했다. 정상 프로세스에서 통화를 유지한 `node web/scripts/native-telephony.mjs`는 SMS/발신 번호/ringing/ended를 모두 통과했다. Cloud Chrome 시험은 시험 E2EE 키 설정의 비동기 저장이 계측 종료 전에 확정되도록 commit한 뒤 실제 복호화·IDB 저장을 통과했다. 비동기 IDB 상태도 명시적으로 await하여 이전의 수신 미확인 결과와 구분한다. 보호된 PHONE_STATE를 테스트용 Intent로 보내는 우회는 없다. 새 버전의 물리 Galaxy/iPhone 인수 시험은 남아 있다.

1.1.0 기능 검증 시 Worker/PWA version: `e6345f6d-61f8-46ac-9577-c7865bc3aa65`. 이후 디자인 배포는 위 추가 결과를 따른다. 아래는 2026-10-03 최초 버전의 검증 이력이다.

검증일: 2026-10-03, Windows / Node.js 22.13 / JDK 21 / Android SDK36. Android 실행 환경은 Google Play services가 있는 Android 15(API35) 에뮬레이터 `emulator-5580`이다. PWA는 Chrome 153 / Playwright 1.63에서 실행했다. **물리 Galaxy 또는 iPhone은 연결되어 있지 않아 해당 기기의 OS/앱 시험은 미검증이다.**

## Phase 순서와 실행 확인

2026-10-04 추가 사용자 보고: 삼성 기기 두 대에서 정상 동작했고 실제 iPhone 잠금 화면에 Relay Push가 도착한 사진도 확인했다. 위의 자동 시험과 별개의 실제 기기 사용자 보고다. 개별 시나리오·기기/OS 버전·E2EE 여부는 아직 미기록이며, 새 버전의 전화 종료/권한/클릭 동작은 인수 검증이 남아 있다. 자세한 다음 작업은 [작업 기록](work-log.md)을 따른다.

| Phase | 구현·실행 확인 | 결과 / 다음 단계 |
|---|---|---|
| 1 Backend | Worker/KV/VAPID/FCM v1, mock API clients, 독립 HTTP socket, 실제 암호화 transport 검증 | 독립 Backend 테스트 통과 후 새 Android 프로젝트를 생성 |
| 2 Android | Java/Gradle/Manifest 신규 프로젝트, 첫 단계 register/pair/test event/FCM, 이어 NLS/Room/SMS/전화/명령 추가 | APK 빌드·JUnit·Lint·API35 설치/Room/Keystore/Firebase token 및 실제 Backend pairing 통과 후 PWA 구현 |
| 3 PWA | 설치 manifest/SW/Push/IndexedDB, 이어 pairing/상세/open/dismiss/ACK/상태·오프라인 | 브라우저 시험 통과 후 통합 |
| 4 통합 | 실제 Android 원본 Notification → 배포 Worker → Chrome Web Push → IndexedDB, FCM 열기 확인 알림/삭제/중복/제거 상태 | 기본 전송 통과 후에만 E2EE 추가 |
| 5 보안·안정성 | AES-GCM E2EE, crypto 상호 운용, tamper/downgrade 거부, 재시도 CAS·상한·credential revoke 개선 | 암호문 실제 클라우드 전송 및 직접 OPEN/ACK 통과. 최종 오프라인/전화/SMS 시험 결과는 아래에 기록 |

## 자동 시험

| 시험 | 결과 |
|---|---|
| Backend TypeScript `npm run check` | PASS |
| Backend Vitest 13 tests / 3 files | PASS |
| health, token 거부, 등록, code 생성/claim/승인/만료/재사용, Web Push 등록 | PASS mock clients; 등록/pairing/FCM은 실제 배포 API도 확인 |
| mock event/command, HMAC/역할/ACK 설정, schema/body 크기/SSRF/stale timestamp/rate limit | PASS |
| Web Push 실패/만료·FCM 실패·양쪽 revoke | PASS provider 실패를 모의하여 오류/정리 확인 |
| Web Push ECDH/aes128gcm payload 및 OAuth RS256/FCM 구조 | PASS transport 암호화·서명 시험 |
| opaque E2EE envelope relay, extra 평문 필드 거부, event 시 KV에 기록 안 함 | PASS |
| stale device metadata 설정/heartbeat 갱신이 새 Push 구독을 덮어쓰지 않음 | PASS 회귀 테스트. 서로 다른 상태를 독립 KV 키로 갱신 |
| Android assembleDebug / assembleDebugAndroidTest / JVM 2 tests / lintDebug | PASS. Lint error 없음; 플랫폼/library 업데이트 및 deprecation 등의 warning은 남음 |
| API35 Room 영속 claim/nonce dedup, Keystore 로컬 본문 암호화 | PASS 계측 시험 |
| Firebase 실제 token·HTTPS 등록·12자리 코드·승인·FCM PING 전송 | PASS 계측 시험 |
| PWA Node 6 tests | PASS. payload/merge/removal ordering 및 E2EE/tamper/wrong-key/downgrade |
| PWA Playwright 7 tests | PASS. 실제 Service Worker handler→IDB→Notification, 보관 5,000, XSS 안전 렌더링, 검색/필터, 오프라인, 상세만 ACK, open/dismiss requests, pairing, 갱신 전 본문 보존 |
| Java AES-256-GCM → 실제 Web Crypto 복호화 | PASS 계측 fixture와 Node 상호 운용, 변경 IV/매번 새 IV/변조된 AAD·ciphertext·잘못된 키 거부 |
| 배포 HTTPS PWA 및 실제 Chrome Push subscription/register | PASS |
| 실제 암호문 Android→Worker→Web Push→IndexedDB, FCM 직접 OPEN/확인 알림/DISMISS/ACK/중복 | PASS |
| 수정한 Queue의 실제 네트워크 단절→복구 | PASS. 에뮬레이터 wifi/data 단절 중 Room 보관 → 복구 후 Worker/Push/IndexedDB 도착 |
| emulator native SMS_RECEIVED / 전화 ringing·ended → 실제 Push | PASS. 실제 수신기·가상 모뎀 → AES-GCM → 배포 Worker → Chrome Web Push → IndexedDB |
| 서명된 Release assembleRelease / lintRelease / apksigner verify | PASS. RSA 3072-bit 전용 로컬 서명, APK signature v2 검증, min26/target36, debuggable 아님 |
| Release APK 실제 설치·초기 화면 실행 | PASS API35 에뮬레이터. Activity COLD launch 성공, 화면 확인, run-as가 package not debuggable로 거부됨 |
| 실제 테스트 Pair 폐기 | PASS 배포 `/api/unpair`. 시험용 양쪽 credential/등록 관계 삭제 후 시험 APK 정리 |

Push 서비스가 요청을 수락하는 API 테스트와 브라우저에서 실제 알림/기록을 받은 검증을 구분했다. 자동 UI ACK 시험은 browser mock API이며 실제 FCM ACK는 Android 계측 통합 시험에서 별도 확인했다. iOS Notification 클릭은 실제 iPhone에서 추가 확인해야 한다.

전화 시험은 고정 4초 대기 후 종료하는 첫 시도에서 SMS=true/call=false로 실패했다. [공식 emulator console 명령](https://developer.android.com/studio/run/emulator-console)에 따라 가상 수신을 발생시키고 telephony.registry의 실제 ringing 전환을 기다린 후 종료하도록 시험 harness를 수정했다. 수정 후 native SMS/ringing/ended와 실제 브라우저 수신을 모두 통과했다. 보호된 PHONE_STATE broadcast를 임의로 보내거나 테스트를 receiver 직접 호출로 대체하지 않았다.

최종 Worker 배포 version: `3928b098-5700-42c8-a034-4abaf4947be4`. 공개 실행 주소는 README에 있다. Release APK의 공개 파일 무결성 값은 `artifacts/SHA256SUMS.txt`에 기록한다. APK/ZIP export는 실제 로컬 설치 비밀·FCM private key·device token·서명 비밀번호와 비교해 포함 여부를 검사한다.

## 재현

```sh
cd backend
npm ci
npm run check
npm test
cd ../android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :test-fixture:assembleDebug :app:assembleDebugAndroidTest
cd ../web
npm ci
npm test
npx playwright install chromium
# 별도 터미널: backend에서 npm run dev (8787)
npm run test:browser
```

실제 integration은 전용 emulator(5580) 및 Chrome가 필요하다. 테스트 전용 fixture/test APK를 설치하고 emulator에서 NLS 및 알림 표시 권한을 허용한다. 테스트 코드가 기기 등록·테스트 pair를 생성하므로 개인 실제 기기에 계측 APK를 설치하지 않는다.

```sh
cd backend
node scripts/emulator-config.mjs
adb -s emulator-5580 shell am instrument -w -r -e class kr.infosphere.galaxyrelay.RuntimeTest kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner
node scripts/export-test-link.mjs
cd ..
node web/scripts/live-browser.mjs --integration --encrypted --offline --telephony
node web/scripts/native-telephony.mjs
node web/scripts/crypto-interop.mjs
```

먼저 emulator에 `app-debug.apk`, `app-debug-androidTest.apk`, `test-fixture-debug.apk`를 설치한다. `cmd notification allow_listener kr.infosphere.galaxyrelay/kr.infosphere.galaxyrelay.RelayNotificationListener`, 앱과 fixture의 POST_NOTIFICATIONS, 선택형 READ_PHONE_STATE/RECEIVE_SMS가 필요하다. Node script는 test-only `.secrets/test-link.json`을 사용하며 credential/key를 출력하지 않는다. Emulator SMS/통화는 에뮬레이터 가상 모뎀에서만 발생하고 실제 번호로 통신하지 않는다. `--offline`은 테스트 emulator의 wifi/data를 잠시 끄고 finally에서 복구한다. test fixture가 원본 contentIntent를 제공하며 Relay 앱 APK에는 fixture/test credential이 포함되지 않는다.

WorkManager가 대기 체인을 제때 재개하지 않는 문제가 실험에서 발견돼 unique replacement 작업 + 프로세스 내부 단일 재시도 타이머로 수정했다. 영속 Room/WorkManager를 유지하며 프로세스가 없는 경우 OS 스케줄러 제약을 따른다. 일부 실제 Push 요청에 일시 네트워크 실패가 있어 안전한 오류 분류 및 backoff를 확인했다. 테스트 통과가 Doze나 서버 장애 중 5초 정시 실행을 보장하는 것은 아니다.

## 실제 기기 인수 시험 (아직 미검증)

| 사용자 시나리오 | 실제 기기에서 확인할 조건 |
|---|---|
| A KakaoTalk 김철수 · 야 어디야? | Galaxy 앱 필터·원본 본문·iPhone Push title/body 확인. 일반 relay/mock/SW 데이터 경로는 통과, 실제 KakaoTalk APK는 미검증 |
| B 폰에서 열기 | 잠금 해제 foreground 직접 열기, background Galaxy 확인 알림 탭으로 원본 채팅방 이동, 원본 사라짐/Intent 취소 오류 |
| C Galaxy에서 지우기 | 선택한 원본만 Notification panel에서 제거. 메시지 서버 읽음과 별개인지 확인 |
| D 실제 iPhone 상세 조회 + 자동 삭제 ON | 홈 화면 PWA 알림 클릭→visible 상세에서 ACK. Banner만 표시할 때는 삭제 안 됨 |
| E Galaxy swipe 제거 | onRemoved → 임시 상태 receipt → 열린 PWA 조회 → Galaxy에서 제거됨. 긴 오프라인에서는 best-effort |
| 전화/SMS | 실제 통신사/One UI 권한·번호 제공·직접 SMS 제한·기본 메시지 앱 중복 방지 |
| 장기 운영 | 재부팅/Doze/One UI 절전/배터리/앱 강제 종료/잠금 상태/API36 이상/Focus·iOS 요약/IndexedDB 축출 |

실제 기기 테스트를 수행하기 전까지 “자신의 Galaxy/iPhone에서 모든 시나리오가 통과했다”라고 주장하지 않는다. 설치 가능한 APK와 배포 PWA를 제공했지만 마지막 인수 검증에는 사용자 소유 기기의 실행이 필요하다.
