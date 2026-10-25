# 작업 기록 및 다음 작업

## 2026-10-10 새 알림함 디자인

- Linear Inbox·Notion Inbox·Material 목록 레퍼런스를 조사했다. 자체 CSS/SVG로 밝은 바탕·불투명 카드·보라색 강조의 알림함을 구현했다.
- 큰 시계·배경·WebGL 굴절을 현재 앱에서 제외했다. 검색/분류 상시 노출, 날짜별 구역, 읽지 않은 수·묶음 개수, 모바일 하단 상세/답장, 설정 그룹과 시스템 다크 모드를 적용했다.
- 기존 상세 조회 기록 유지·좌측 swipe 삭제·Galaxy 제거 동기화·상세 재탭 OPEN·일반/E2EE 답장을 유지했다.
- Node 12 / Chrome 29 PASS. Windows WebKit 온라인 화면/레이아웃 2 PASS; offline reload는 엔진 내부 오류로 미검증.
- 기존 주소에 배포했다. version 12a449ca-fcdf-4c96-b493-13489a514241, 공개 status 200 및 최종 다섯 정적 파일 해시 일치. 배포 파일로 합성 미리보기를 생성해 확인했다.
- APK 변경 없음. PWA 닫기/재실행으로 갱신. 실제 iPhone 새 화면 확인이 남아 있다. [현재 디자인](inbox-design.md), [검증 결과](test-results.md).

## 2026-10-09 PWA 답장

- `/api/command/reply`, bounded plain/E2EE reply payload, reply 전체 HMAC, content-free 실행 receipt를 구현하고 기존 주소에 배포했다. KV/로그에 답장 본문·암호문을 저장하지 않는다.
- Android Java 1.2.0/versionCode 3: 원본 자유 입력 RemoteInput capability 수집, 현재 Notification/postTime/안정적인 액션 지문 검사, 잠금 거부, 원본 앱의 mutable non-Activity PendingIntent 실행, UUID/nonce 중복 억제. 알림 접근 재연결 시 활성 알림을 다시 수집한다. Android 12 이상에서만 답장 제공; 알림 전달 min26은 유지.
- PWA 상세에 500자 답장 form과 조용한 결과 표시. 기존 상세 재탭 OPEN·좌측 swipe DISMISS·읽은 본문 보존은 유지한다. 미지원·원본 갱신·오프라인 비활성화, 재열기/reload 포함 대기 중 중복 전송 방지, 자동 재전송 없음.
- E2EE 답장에는 기존 AES-256 키와 새로운 authenticated domain prefix를 사용한다. 기존 decrypt-only non-extractable PWA 키는 같은 키를 한 번 다시 저장해야 한다. 평문 자동 downgrade는 막는다.
- Backend 19 / PWA Node 12 / Chrome 전체 30 / Java 단위 4 / 최종 RemoteInput 계측 2 및 build/lint PASS. 실제 배포 PWA→Worker→FCM→원본 fixture 답장과 조용한 receipt를 평문·E2EE로 통과했고 동일 명령 재전송의 중복 실행 없음도 확인했다. 테스트 Pair 폐기 완료.
- Windows WebKit 추가 답장 시험은 모의 POST capture 및 UI automation timeout으로 미검증이며 전체 성공으로 기록하지 않는다. 물리 Galaxy/iPhone과 실제 메신저 답장 시험은 남아 있다.
- 기존 Release와 같은 서명으로 APK 1.2.0 업데이트를 제공한다. PWA도 닫기/재실행해 갱신한다. [설치·사용·보안](reply.md), [실행 결과](test-results.md).

## 2026-10-04 리퀴드 글래스 광학 렌더링

- 기존 PWA 주소에 배포 완료: `44e2be2c-bba7-45eb-b313-c26576d1cc0d`. 변경 파일 4개의 배포 해시·공개 health를 확인하고 배포된 광학 화면을 합성 fixture로 렌더링했다. APK 재설치는 필요 없으며 소스 ZIP·무결성 파일을 실제 secret 제외 검사와 함께 갱신한다.
- 알림·상세 카드의 배경에 공유 WebGL 렌더러를 적용했다. 표면 법선·굴절률 1.47·가장자리 굴절·작은 RGB 분산·곡면 반사광·확대·내부 밝기 변화를 계산한다. 글자·아이콘은 DOM에 남긴다. 외부 유료 리소스/CDN은 추가하지 않았다.
- 고정 배경만 샘플링하며 다른 앱/DOM/알림 본문을 GPU로 보내지 않는다. 보이는 최대 16개 카드, 픽셀 배율 1.5, 최대 약 30fps, 정지·숨김 시 프레임 중단, 화면 밖 캔버스 해제를 적용했다. WebGL 미지원·컨텍스트 손실·투명도 감소에 대응한다. 캐시와 저장된 연결 레이아웃의 오프라인 초기화도 보완했다.
- Chrome 전체 25개 PASS. WebKit 온라인 그래픽 3개 PASS, 오프라인 reload 1개는 Windows WebKit 내부 탐색 오류로 실패/미검증. 전체 성공으로 처리하지 않는다. 새 버전의 물리 iPhone 시각 효과·GPU 성능·오프라인 인수 시험은 남아 있다.
- 첫 픽셀 시험에서 교체/해제되는 이전 캔버스를 읽던 경합을 실제 연결·픽셀의 한 스냅샷을 기다리도록 고쳤다. 초기 레이아웃 이동을 저장된 연결 여부로 첫 화면부터 줄였고, 상태 순회 시험은 실제 인증 실패와 섞이지 않도록 HTTP fixture를 격리했다. 이후 Chrome 전체 회귀를 통과했다. [설계·제한·재현](liquid-glass.md).

## 2026-10-04 상세 조회 기록 유지

- 기존 PWA 주소에 배포 완료: `039cdaa6-f9a2-495d-9608-d44d43c5be1c`. 변경 정적 파일 4개의 해시와 health, 배포 화면의 합성 fixture로 상세 조회 후 7개 기록 보존·읽음 1개를 확인했다. 소스 ZIP 및 무결성 파일도 secret 제외 검사와 함께 갱신했다.
- 사용자의 최신 요청에 따라 상세 조회에서 본문 삭제를 중단하고 `read=true`만 기록한다. 닫기·재조회·새로고침·오프라인 실행 후에도 기록과 본문이 유지된다. 읽지 않은 개수는 감소한다.
- 시작 시 이관도 읽음 행을 지우지 않는다. 왼쪽 밀기 및 Galaxy 원본 제거 동기화, 전체 기록 삭제, 5,000개 상한은 유지한다. 상세 재탭 OPEN과 자동 ACK 금지도 유지한다.
- 이전 버전에서 이미 지운 본문은 서버에 저장하지 않았으므로 복구하지 않는다. iPhone의 다른 네이티브 앱 알림 조회·삭제·읽음 감지는 제공할 수 없으며 해당 앱 자체 동기화와 구분한다.
- Node 10개 / Playwright 21개 PASS. 조회 후 본문 보존·읽음 유지·닫기 및 다시 열기·새로고침·동일 이벤트 재전송·새 메시지 읽지 않음·오프라인 재조회·기존 읽음 행 이관과 스와이프/제거 회귀를 확인했다. 물리 기기의 새 버전 인수 시험은 남아 있다. Android/Backend 소스는 변경하지 않았다.

## 2026-10-04 제스처·묶음 알림

- 기존 PWA 주소에 배포 완료: `1b8a7b21-bc73-480b-9318-89f7dc057248`. HTTP health 및 최종 정적 파일 8개의 SHA-256 일치를 확인했고 배포 주소의 실제 화면을 합성 fixture로 렌더링했다. Android APK는 변경하지 않았다. 소스 ZIP 및 무결성 파일을 갱신하며 실제 secret 제외 검사를 수행한다.
- 앱 + 발신자/연락처/제목별로 알림을 묶고 개수 배지·유리 스택을 표시한다. 제목이 없으면 앱 단위로 묶는다. 묶음을 누르면 모든 항목을 펼치며 기록을 확인 처리하지 않는다.
- 개별 알림 상세를 실제로 열면 로컬 기록만 지운다. 상세 카드를 다시 누르면 기존 원본 OPEN 명령을 전송한다. 별도 열기·삭제 버튼과 자동 ACK 옵션은 제거했다. 기존 서버 설정이 ON이어도 새 PWA는 ACK를 보내지 않는다.
- 충분한 좌측 스와이프/키보드 Delete로 해당 활성 Galaxy 알림을 DISMISS하고 PWA 기록을 삭제한다. 묶음 삭제는 원본별로 중복 없이 요청한다. 오프라인에서 원본 동작이 필요한 항목/즉시 provider 실패는 남기며, 일부 명령 접수 후 실패하면 남은 항목을 유지한다. 실제 기기 실행 전이라도 FCM 접수에 성공한 삭제는 낙관적으로 표시한다.
- Galaxy 원본 제거 receipt를 적용하면 PWA 기록을 삭제한다. sourceId 해시로 같은 Notification의 갱신 기록을 연결해 최신 본문을 상세에서 지운 뒤에도 이전 기록을 정리한다. 늦은 갱신은 제거 상태를 되살리지 않으며 원본이 없는 직접 SMS/전화 기록의 스와이프는 로컬 기록만 삭제한다.
- 상태 조회를 20개씩 돌아가며 수행해 오래된 기록도 검사한다. 제거 receipt TTL(1시간), KV 전파, 기기/브라우저 오프라인에 따른 best-effort 한계는 유지한다. 원본 앱 서버의 읽음 상태나 Android OS의 화면 실행 제한을 우회하지 않는다.
- Node 10개 / Playwright 21개 PASS. 실제 포인터 제스처, 묶음 펼치기, 자동 ACK 금지, 재탭 OPEN 요청, 상세 스와이프, 짧은/세로 밀기 오작동 방지, 즉시 실패 시 유지, 갱신 family 삭제, 45개 상태 순회, 기존 read/removed 기록 이관, 기존 Push/통화/crypto 회귀 확인. 스와이프 중 렌더링 경합과 시험 좌표 조회 경합을 수정했다. 물리 iPhone/Galaxy 새 동작은 미검증이며 APK/Backend 코드는 변경하지 않았다.

## 2026-10-04 PWA 유리 화면·확인 기록 삭제

- 사용자가 제공한 Wallpaper.png를 그대로 정적 배경으로 사용하고 날짜·분 단위 실제 시계, 읽지 않은 수, 앱 구분 아이콘과 유리 카드를 구현했다. 검색·분류를 접어 기본 화면을 정리했다. 이 디자인은 PWA 내부이며 시스템 잠금 화면이나 Push 배너를 제어하지 않는다.
- 상세 화면을 실제로 연 알림은 IndexedDB 본문 기록에서 삭제한다. 열려 있는 상세의 메모리에서 명령 기능을 유지하며, 내용 없는 확인 ID tombstone은 최대 5,000개로 제한한다. 삭제/통화 상태 조회가 tombstone을 덮어써 기록을 되살리지 않도록 보완했다.
- 상세를 닫거나 새로고침하면 내용을 다시 열 수 없다. 기록 삭제는 원본 Galaxy 알림 제거와 별개다. 기존 자동 ACK 옵션과 오프라인 조회·명령 차단은 유지한다.
- Node 8개 및 브라우저 13개 시험 통과. 320/390/430px 가로 넘침, 배경 offline cache, 상세 저장 삭제·재전송 방지·실제 상세 명령 결과 동기화 검증. 로컬 합성 fixture로 light/dark/detail 렌더링도 확인했다. Android 코드/APK는 변경하지 않았다.
- 기존 PWA 주소에 배포 완료: `0bfd6d23-9ec9-446e-adbd-3651b13e553c`. 배포된 실제 정적 파일의 해시 일치와 HTTP health, 합성 fixture를 사용하는 새 browser context의 화면·목록 삭제를 확인했다. PWA 닫기/다시 열기로 갱신하며 기존 연결·키·미확인 알림을 초기화하지 않는다. 소스 ZIP 갱신에 실제 secret 제외 검사를 적용한다.

## 2026-10-04 추가 구현 · 1.1.0

사용자가 실제 iPhone 잠금 화면 Push 수신을 확인했다. 해당 사진의 개인정보는 이 기록에 옮기지 않는다. 새 요청에 따라 다음을 구현했다.

- 메시지 Push 제목을 `발신자 · 앱`으로 표시. iOS의 Relay 앱 이름·아이콘은 유지하며 미리보기/이름 숨기기를 존중한다.
- 삭제·ACK·열기 결과·연결 시험·통화 종료를 별도 Push로 보내지 않고 인증된 상태 조회로 변경. 이전 암호화 control Push까지 없애려면 Android도 업데이트한다.
- 선택형 발신자 번호·연락처 권한, 번호 한 건의 연락처 조회, 전화 앱 Notification.Person/제목 보강, 번호 없는/있는 중복 broadcast를 같은 callId로 처리.
- 통화 연결·거절·종료 상태가 도착하면 기존 전화 수신 배너만 닫고 같은 IndexedDB 통화 기록을 갱신. 늦게 도착한 수신 이벤트가 종료 상태를 되돌리지 않도록 tombstone을 유지한다.
- PWA 갱신 중 중복 ACK 방지, 만료 상태 키·연결 해제 정리, 전화 수신기 처리와 네트워크 재시도 작업의 스레드 분리.
- Release 1.1.0/versionCode 2, 기존 서명 유지. Worker/PWA 기존 URL 배포. 소스·APK·SHA-256 export에서 실제 secret 제외를 검사한다.

상태 receipt는 본문·제목·번호·이름 없이 ID/시간/결과만 TTL로 보관한다. PWA가 완전히 닫힌 iPhone에서 새 배너 없이 즉시 깨워 수신 배너를 닫는 동작은 WebKit 정책상 보장할 수 없다. 열려 있는 동안/다시 열 때/다음 실제 메시지 Push 수신 때 정리한다. KV 전파·OS 알림 API도 best-effort다. 자세한 검증 결과와 남은 인수 시험은 [테스트 결과](test-results.md)를 따른다.

최종 변경 검증: Backend 17개, PWA Node 8개/브라우저 10개, Android 빌드·lint·전화 계측을 통과했다. 실제 배포 환경에서 Android 암호화 Push → Chrome 저장, 가상 모뎀 발신 번호 → 전화 배너, 조용한 종료 조회 → 배너 닫기도 통과했다. 기존 OPEN 직접 실행 경로의 마지막 전체 재시험은 원본 Activity 전환에서 실패했으므로 별도로 남겼고 후속 단계 성공으로 처리하지 않았다. 새 버전의 물리 Galaxy/iPhone 확인은 남아 있다.

아래 첫 기록의 ‘iPhone 미시험’은 최초 기록 시점의 상태다. 이후 실제 iPhone Push 수신 사진이 제공됐다. 백그라운드 강제 직접 열기·앱별 iPhone 배너 아이콘 변경은 이번 요청의 변경에 포함하지 않았다.

## 2026-10-04 · Asia/Seoul

### 오늘 확인한 내용

- 사용자가 삼성 기기 두 대에서 Galaxy Relay가 정상적으로 작동하는 모습을 확인했다고 보고했다.
- 보내는 기기는 신규 Java Android Relay 앱, 받는 기기는 Chrome의 설치형 PWA로 시험했다. UI의 ‘iPhone’ 명칭은 받는 삼성 기기에도 동일하게 표시된다.
- 개별 기능별 시험 결과, 두 기기의 모델·Android/One UI 버전, E2EE 활성화 여부는 아직 기록되지 않았다. 전체 인수 시나리오를 모두 통과했다고 확대 해석하지 않는다.
- 실제 iPhone Safari/PWA 시험은 아직 진행하지 않았다. 삼성 기기 시험이 iOS Web Push 검증을 대신하지는 않는다.
- Android 앱의 등록·권한·페어링·앱 필터·문자·전화·Queue·최근 이벤트·연결 해제·E2EE 설정 및 받는 PWA의 기능별 사용법을 안내했다.

### 사용자 질문과 확인한 현재 동작

1. **PWA의 ‘폰에서 열기’ 클릭으로 보내는 Galaxy에서 즉시 원본 앱 열기**
   - 현재 `CommandHandler`는 Relay 화면에 실제 포커스가 있고 잠금이 해제된 경우 원본 Notification의 `contentIntent`를 직접 실행한다.
   - Relay가 백그라운드일 때는 Galaxy에 ‘iPhone에서 열기 요청’ 확인 알림을 표시한다. 사용자가 그 알림을 누르면 원본 `contentIntent`를 실행한다.
   - Android의 Background Activity Launch 제한 때문에 원격 PWA 클릭만으로 무조건 자동 실행을 보장할 수 없다. ‘다른 앱 위에 표시’ 권한을 활용하는 선택형 모드는 검토 가능하지만 현재 구현되어 있지 않다.
   - `PendingIntent.send()`가 예외 없이 반환해도 OS가 화면 실행을 차단할 수 있으므로 단순 try/catch를 성공 판정으로 삼지 않는다. 잠금 화면 우회, 접근성 자동 클릭, 추측한 앱 deep link는 도입하지 않는다.
   - 근거: [Android Activity security](https://developer.android.com/guide/components/activities/secure-bal).

2. **알림마다 앱별 아이콘 표시**
   - 현재 Service Worker는 모든 Push에 고정 Relay `icon`/`badge`를 사용하며 PWA 목록에는 앱별 그림이 없다.
   - PWA 목록·상세의 앱별 아이콘과 Android Chrome Push의 `icon`/`badge`는 추가할 수 있다. 보내는 Galaxy의 열기 요청 알림에도 원본 앱의 큰 아이콘을 추가할 수 있다.
   - 원본 앱별 아이콘을 표시해도 알림 발행 주체는 Relay/PWA다.
   - iPhone Push 배너의 개별 아이콘 교체는 보장하지 않는다. PWA 앱 아이콘을 기본으로 하고 앱 이름으로 구분한다. 실제 iPhone 표시 검증은 남아 있다.
   - 근거: [showNotification 옵션](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerRegistration/showNotification), [WebKit 미해결 icon 이슈](https://bugs.webkit.org/show_bug.cgi?id=280162).

### 내일 이어갈 작업 · 2026-10-05 예정

- [ ] 사용자 테스트 기기의 모델·Android/One UI 버전과 통과한 개별 시나리오를 추가 기록한다.
- [ ] 현재 직접 열기부터 실제 기기로 검증한다: 보내는 Galaxy 잠금 해제 → Relay 화면 유지 → 받는 PWA ‘폰에서 열기’ → 원본 화면 이동 확인.
- [ ] 백그라운드 직접 열기 선택 옵션의 허용 조건·권한·보안 영향을 설계하고 README에 이유를 기록한다. 구현한다면 기본 OFF, 사용자 명시적 활성화, 잠금 상태 제외, 확인 알림 fallback을 유지한다.
- [ ] 기본 동작이 확인된 현재 버전을 보존하면서 앱별 아이콘을 PWA 목록·상세와 Android Chrome Push에 추가한다. 아이콘은 안전한 로컬 정적 리소스를 우선 사용하고 외부 임의 URL을 받아 다운로드하지 않는다.
- [ ] 직접 열기·아이콘 변경 후 관련 자동 시험, Android build/lint 및 실제 삼성 기기 재시험을 수행한다.
- [ ] 개선이 검증되면 기존 Release 서명 키로 APK를 갱신하고 PWA 배포·source ZIP·SHA-256·문서를 갱신한다. 기존 설치에 업데이트하려면 동일 signing key를 유지해야 한다.
- [ ] iPhone을 사용할 수 있을 때 홈 화면 설치·Push 수신·알림 클릭·상세 ACK·배너 아이콘을 별도로 확인한다.

아이콘 변경이나 백그라운드 직접 열기 옵션의 코드 수정·재배포는 오늘 수행하지 않았다. 오늘 마지막 변경은 이 기록 및 검증 문서의 사용자 시험 보고 반영이다. 자동 예약 실행이나 알림은 설정하지 않았다.

### 이어서 작업할 때 참고

- 프로젝트: `galaxy-relay/`. Backend/Android/PWA를 다시 생성할 필요가 없다.
- 배포: [Galaxy Relay](https://relay.example.com).
- 설치 APK: `artifacts/GalaxyRelay.apk`. Source: `artifacts/galaxy-relay-source.zip`.
- 코드: `android/app/src/main/java/kr/infosphere/galaxyrelay/CommandHandler.java`, `OpenNotificationActivity.java`, `RelayNotificationListener.java`, `web/service-worker.js`, `web/src/app.js`.
- 검증: [test-results.md](test-results.md). 기존 Backend 13개, PWA browser 7개 및 실 클라우드/에뮬레이터 통합 시험 통과 기록을 유지한다.
- 비밀은 `.secrets/`, Android signing 설정은 Git 제외 `android/release-signing.properties`에 있다. 내용을 이 기록에 복사하거나 Git/source ZIP에 넣지 않는다.
- `sources/`는 읽기 전용 참조 자료이며 수정하지 않는다.


## 2026-10-14 네이티브 2.0 전환

Java BLE peripheral/QR auth, SwiftUI/CoreBluetooth native client, GitHub macOS unsigned IPA, native encrypted foreground cloud pull/FCM commands/replies를 추가. 무료7일 서명은 AltStore Refresh, 서버 background APNs는 제외. Backend21/Android7/Swift4 tests 및 emulator/simulator 실행과 실제 Worker/FCM 합성 RemoteInput 시험 통과. 물리 iPhone BLE/AltStore 설치 인수 확인은 남아 있음. 상세는 native-ios-setup.md/native-architecture.md/native-tests.md.
