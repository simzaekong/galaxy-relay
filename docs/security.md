# 보안 설계와 한계

PWA 상세 조회는 읽음 표시만 하며 본문은 IndexedDB에 최대 5,000개 유지한다. 상세를 닫을 때 DOM을 비우지만 기록은 다시 열 수 있다. 스와이프 삭제와 Galaxy 원본 제거 동기화는 본문을 지우고 중복 복원을 막는 ID·삭제 시간·최소 상태를 로컬 `states`에 최대 5,000개 유지한다. 이는 브라우저 저장 공간의 보안 삭제나 forensic recovery 불가능을 보장하지 않는다. 전체 기록 삭제는 해당 상태도 지운다. 상세 조회 시 자동 ACK를 보내지 않으며 PWA 읽음 표시는 원본 앱 읽음/원본 Notification 제거를 뜻하지 않는다. 원본 삭제는 스와이프에서 요청한다. 원본 알림의 갱신 기록 연결에는 앱 범위의 notificationKey를 SHA-256으로 해시한 로컬 sourceId를 사용한다. 이 해시·ID·최소 상태에 제목·본문·연락처 이름을 넣지 않고 서버로 보내지 않는다.

## 인증·연결

모바일 요청은 HTTPS만 사용하고 device credential은 CSPRNG 32 byte(256 bit) bearer token이다. KV에는 SHA-256 hash와 device metadata를 저장한다. 토큰을 URL에 넣지 않는다. 최초 Android 등록에는 별도 INSTALL_SECRET이 필요하다. 공개 endpoint를 누구나 이용해 무제한 등록하는 구조를 피했다. 이 secret은 APK에 내장하지 않는다.

페어링 코드는 rejection sampling으로 생성한 12자리 숫자, 5분 유효, 순차 claim 1회다. `/pair/claim` 후에는 이벤트/명령 relay가 허용되지 않는다. Galaxy에서 8자리 승인 코드를 직접 비교해야 서로 일치하는 peer 관계가 생성된다. Claim 대기 credential은 10분 TTL로 제거되며 승인 후 장기 credential이 된다. 새 페어링 코드를 만들면 이전 pending credential을 폐기한다.

Cloudflare Rate Limiting binding으로 등록/claim/create는 IP당 5회/분, 인증 API는 device당 60회/분을 제한한다. 같은 개인 회선 기기가 IP를 공유하면 제한을 공유한다. binding은 지역·best-effort이고 공격에 대한 전역 정확한 counter는 아니다. KV에는 알림마다 rate counter를 쓰지 않는다.

**KV 제약:** KV에는 전역 선형 일관성·원자적 consume·transaction이 없다. 여러 edge의 동시 claim 경쟁, stale peer, token revoke 반영 지연을 완전히 제거할 수 없다. Android의 최종 승인과 양방향 관계 확인, 명령 실행 전 상태 재확인으로 위험을 줄인다. 엄밀한 전역 원자성은 별도 강한 일관성 저장소가 필요하지만 사용자 지정 KV-only 범위 때문에 추가하지 않았다. [Cloudflare KV 공식 설명](https://developers.cloudflare.com/kv/concepts/how-kv-works/)을 따른다.

인증된 `/api/status`의 `pushFingerprint`는 자신의 PWA 또는 승인된 peer의 Push endpoint SHA-256이다. 최신 구독이 전송 경로에 반영됐는지 확인하기 위한 metadata이며 원문 endpoint/구독 키를 반환하지 않는다. 공개 health 응답에는 포함하지 않는다.

## 입력·재생 공격

- Content-Type JSON, streaming body 상한 8 KiB, 타입·문자 길이·UUID·subscription key 형식 검증
- 외부 요청 timestamp ±5분, 명령 수신 ±2분, nonce UUID 및 commandId UUID
- FCM 명령 HMAC-SHA256: commandId/type/eventId/timestamp/nonce/autoDismiss를 고정 순서로 서명
- Android Room의 commandId primary key + nonce unique claim을 효과 실행 **전** 저장하여 동일 명령 실행 방지
- Android notification eventId를 로컬 원본 key에 매핑; PWA eventId+timestamp durable dedup
- Event timestamp와 종류별 Queue TTL로 오래된 알림 제한
- Push endpoint는 Apple/Google/Mozilla의 명시적 HTTPS hostname만 허용하고 redirect를 따라가지 않아 SSRF를 제한
- same-origin 요청, API no-store, CSP self/no inline/frame-ancestors none, textContent 렌더링, 정적 리소스만 Cache API 사용

서버는 요청 nonce·commandId 전체 기록을 KV에 보관하지 않는다. 캡처된 정상 토큰 요청이 시간 창 안에서 다시 제출되면 relay 요청 자체는 반복될 수 있다. 명령은 receiver에서 실행이 한 번이고 이벤트는 iPhone 기록에 중복 저장하지 않는다. 동일 tag Push가 갱신될 수 있다. 이는 KV-only와 개인 무료 운영의 명시적 선택이며 서버 자체의 전역 exactly-once를 주장하지 않는다. OS 동작 직전에 앱이 종료되면 이미 claim한 명령을 재실행하지 않는 **at-most-once** 선택이다.

## E2EE 설정

기본 HTTPS + Token 전달을 확인한 후 설정한다. 초기값은 OFF다.

1. 잠금 해제한 Galaxy에서 알림 전달을 잠시 끈다.
2. **E2EE 키 생성·표시**를 누른다. Android SecureRandom으로 32 byte 키를 생성하고 Keystore AES-GCM으로 로컬 보관한다.
3. Galaxy에 표시된 43자리 base64url 키를 **눈앞의 신뢰한 iPhone** PWA 설정의 개인용 E2EE 입력에 직접 옮긴다. 이메일·메신저·Relay 이벤트·서버 요청으로 보내지 않는다.
4. **E2EE 수신 키 저장**을 누른다. iPhone과 Galaxy의 SHA-256 앞 128-bit 지문(32 hex)이 정확히 같은지 비교한다.
5. Galaxy의 **E2EE 전송 사용**을 켜고 전달을 다시 켠다. 테스트 이벤트를 보낸다. iPhone에서 정상 본문과 Android 상태 이벤트를 읽을 수 있는지 확인한다.
6. E2EE를 해제할 때는 Galaxy 전송 설정과 PWA 수신 키를 모두 해제한다. PWA 수신 키가 있는 동안 평문 Push는 거부한다. 저장 공간을 지우거나 다른 origin으로 옮기면 수신 키도 다시 설치해야 한다.

검증된 AES-GCM을 표준 Android crypto / Web Crypto API로 사용한다. 키 교환 대신 물리적 기기 간 **인증된 out-of-band 무작위 pre-shared key 전달**을 선택했다. 온라인 custom DH/자체 암호 알고리즘은 만들지 않았다. 개인 2기기에서는 서버를 신뢰한 키 교환보다 직접 전달/지문 비교가 단순하다. 다중 사용자·자동 기기 추가·forward secrecy가 필요한 제품용 프로토콜을 제공하는 것은 아니다.

각 전송/재시도마다 새로운 random 96-bit IV와 128-bit GCM tag를 사용한다. AAD는 `GalaxyRelay/v1`, eventId, 원본 timestamp, keyId를 newline으로 연결한 UTF-8이다. keyId/metadata/ciphertext 변조와 잘못된 키는 복호화를 거부한다. 암호문을 풀고 inner id/timestamp 및 event schema를 다시 검사한 뒤 저장한다. 암호문이 Web Push 한도에 맞도록 원문 JSON 2,600 UTF-8 bytes 이하로 본문 끝을 줄일 수 있다.

Worker는 E2EE에서 메시지/발신자 원문의 암호문만 전달한다. eventId·timestamp·keyId·토큰과 전달량은 보인다. FCM 명령에는 본문 없이 eventId와 명령 종류가 있으며 Worker 서명을 신뢰한다. **명령 채널은 본문 E2EE와 별개이고 Worker 운영자는 명령 권한을 가진다.** E2EE용 key/원문 제목/본문/번호/이름은 서버 요청에 포함되지 않는다.

**1.1.0 조용한 상태 조회의 선택:** WebKit의 보이는 Push 요구와 불필요한 상태 배너 제거를 동시에 지키기 위해 본문 없는 receipt만 임시 KV에 저장한다. event/command/call ID, 시간, 제거·통화 연결·종료·명령 결과 enum은 E2EE 본문과 별개의 인증된 HTTPS metadata다. 통화 상태 24시간, 삭제·명령 결과 1시간 TTL이며 연결 해제 시 정리한다. 번호·이름·제목·본문·암호화 본문은 schema에서 거부한다. `/api/sync`는 승인된 peer의 자기 namespace만 읽고 ID 개수·body 크기·시간·rate limit을 검증한다. 서버 운영자는 통화 발생/종료 등의 metadata를 볼 수 있으므로 통화 존재까지 숨기는 E2EE라고 주장하지 않는다. KV 전파 지연과 조회 기회 때문에 상태는 best-effort다.

발신자 권한은 사용자 선택이다. READ_CALL_LOG는 OS의 수신 번호 제공을 위해 요청하며 통화 기록 목록은 조회하지 않는다. READ_CONTACTS는 PhoneLookup에 해당 번호를 넣어 이름 한 건만 얻는다. 전체 주소록 수집/업로드나 임의 외부 프로필 그림 다운로드는 없다. 거절·권한 제한·번호 비공개에서도 전화 상태 전달은 가능하다.

PWA는 non-extractable CryptoKey를 IndexedDB에 저장한다. 이것이 Secure Enclave나 악성 JavaScript 방어를 의미하지 않는다. origin의 코드/Worker 계정이 탈취되면 악성 코드가 키의 복호화 권한을 사용할 수 있다. PWA 코드 배포 계정 및 기기 잠금이 신뢰 경계다. 기록은 복호화된 로컬 데이터이며 이름/본문 숨기기는 저장 암호화 기능이 아니다. 별도 PIN lock을 구현했다고 주장하지 않는다. 키 삭제는 기존 본문 기록을 삭제하지 않는다.

암호 구현 근거: [Android 암호 권장사항](https://developer.android.com/privacy-and-security/cryptography), [Web Crypto 표준](https://www.w3.org/TR/WebCryptoAPI/). Java→Web Crypto 실제 상호 운용, 변조/잘못된 키/평문 거부를 테스트했다. 외부 독립 암호 감사를 받은 제품은 아니다.

## 로컬·Secret·연결 해제

Android credential/FCM token/E2EE key/Room payload를 Android Keystore AES-GCM으로 보호하고 백업 및 device-transfer를 비활성화한다. notificationKey·시간·패키지명 등 로컬 매핑 metadata는 평문이다. Keystore 초기화나 앱 삭제 후 credential을 복원할 수 없으면 재연결해야 한다.

Worker observability를 비활성화하고 본문·subscription·token을 로그에 기록하지 않는다. 에러 응답은 정해진 코드만 반환한다. VAPID private key, FCM service account JSON/private key, INSTALL_SECRET, COMMAND_SECRET은 Cloudflare Secrets/로컬 Git 제외 파일로 관리한다. Firebase Android 공개 client configuration은 서비스 계정 비밀과 구분하며 이 저장소에서는 `google-services.json`도 개인 환경 설정으로 제외한다. 서명 keystore/local.properties/build/알림·테스트 credentials를 Git/ZIP에서 제외한다.

연결 해제는 양쪽 KV device와 token index를 삭제해 peer·subscription·FCM registration metadata를 폐기한다. PWA가 시작한 해제는 로컬 Push unsubscribe도 수행한다. Android가 시작하면 Firebase token 삭제도 시도한다. 원격 기기 브라우저의 OS Push subscription 객체를 서버가 강제로 지울 수는 없으나 서버 metadata와 credential이 사라져 추가 relay가 중단된다. 반대쪽 client는 다음 인증 실패 때 credential을 제거한다. KV 반영 지연 범위에서는 즉시 전역 revoke를 보장하지 않는다.

PWA 기존 기록은 연결 해제와 별개다. **모든 알림 기록 삭제**를 확인하면 notification/status/dedup/command 결과를 지운다. 인증·설정·E2EE key는 유지한다. 완전 삭제는 연결 해제와 Safari/PWA 사이트 데이터 삭제를 함께 수행한다.

## 원격 답장 보안 (2026-10-09)

REPLY는 원본 활성 알림의 action 지문과 원본 앱 소유 mutable non-Activity PendingIntent를 검사한다. 잠금 상태에서는 거부한다. reply 전체를 HMAC에 포함하고 UUID/nonce Room claim으로 재실행을 막는다. PWA는 자동 재전송을 하지 않는다. 기존 E2EE 키로 AES-GCM 답장을 암호화하고, 수신 전용 legacy 키는 재입력 전 평문 전환을 거부한다. 답장 본문/암호문은 KV·로그에 저장하지 않는다. [프로토콜·선택 이유·한계](reply.md).

## 연결 해제 위치

위치는 인증된 Bluetooth 세션의 단절에만 한 번 요청하며, 각 기기 자신이 감지한 위치다. 최신 좌표는 Android Keystore AES-GCM / iOS Keychain에만 보관하며 서버·로그·BLE로 전송하지 않는다. 직접 누른 지도 버튼으로만 외부 지도 앱에 좌표를 전달한다. 기능 끄기·권한 거부·재연결·수동 연결 해제는 조회를 중단하고, 기록 삭제와 페어링 해제는 저장 기록을 제거한다. [시간 제한·권한·OS 한계](disconnect-location.md).

## 선택형 CallKit 수신 화면

iPhone 2.2.0에서 사용자 확인 후 켜는 수신 알림 기능이다. 받기/거절은 이 iPhone의 알림만 종료하며 Galaxy 전화에 명령을 보내지 않는다. 음성/마이크를 처리하지 않고 통화 기록에도 추가하지 않는다. 발신자 미리보기 숨김을 적용한다. 인증된 BLE의 최신 수신만 허용하고 중복·종료 후 재울림·인터넷 지연 수신을 억제한다. iPhone의 이미 진행 중인 다른 통화가 감지되면 일반 알림으로 fallback한다. [사용 의미와 OS 한계](call-alerts.md).

2.2.1의 CallKit 진단은 고정 코드(CK00/CK01 등)와 시각만 UserDefaults에 최신 한 건 저장한다. 발신자·번호·통화 ID·원문 NSError·알림 본문은 포함하지 않고 서버에도 전송하지 않는다. 명시적으로 복사 버튼을 눌렀을 때만 클립보드에 넣으며 기록 삭제도 제공한다. voip는 CallKit 보고용 선언이고 PushKit 등록·APNs entitlement·상시 네트워크 연결·음성 수집을 추가하지 않는다.


## 2.3.0 원격 통화 종료

Galaxy의 ANSWER_PHONE_CALLS 권한을 사용자 선택으로 추가한다. iPhone 받기는 Galaxy에 답신 명령을 보내지 않는다. 거절/종료만 명시적으로 누르면 인증된 live BLE에 서명된 CALL_HANGUP을 한 번 보낸다. 서버/FCM/오프라인 큐에 종료 명령을 저장하거나 재전송하지 않는다. 현재 callId, 단일 수신으로 시작한 상태, 5초 이내 시각, nonce/command ID dedup, 단일 활성 SIM, 실제 전화 상태를 확인한다. 불확실한 통화 대기/다중 SIM/기기 거부에서는 실패한다. endCall 성공 응답만으로 UI를 닫지 않고 실제 ended 상태를 기다린다. 무선 단절·기능 끄기는 iPhone 표시만 종료한다. 통화 시간은 상태 전환 시각으로 계산하며 초마다 송신하지 않는다.


장시간 연결 검사는 기존 인증된 AES-GCM 세션 안에서 UUID probe/response를 사용한다. 이전 세션 응답은 현재 세션/대기 ID를 만족하지 못한다. 공개 endpoint나 테스트 bypass를 추가하지 않는다. 배터리만 포함하며 위치·본문·발신자 정보는 health payload에 없다. 전화 진단은 수신 시각과 고정 상태/설정만 저장한다. health timeout만으로 GPS를 조회하지 않는다.


## 오프라인 전용 앱

network_v1은 기존 인증/암호화 BLE 세션에서 UUID 요청과 고정된 네트워크 상태 값만 전달한다. 앱 선택은 Galaxy에 저장한다. SSID/IP/통신사/접속 기록을 수집하지 않고 추가 HTTP probe를 만들지 않는다. Unknown/응답 없음은 조건부 전달 거부이며 온라인 복귀 시 미전달 전용 이벤트를 폐기한다. Cloud pull/PWA 경로로 우회 전송하지 않는다. 상태는 세션 해제 시 초기화하고 현재 세션 응답만 인정한다. NWPathMonitor가 인터넷 접속 및 개별 앱 push 수신을 증명하지 않는다는 제한을 문서에 명시했다.
