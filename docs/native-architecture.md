# 네이티브 2.0 구조와 보안

기본 경로: Galaxy NotificationListener/Room Queue → BLE peripheral → iPhone CoreBluetooth central → 암호화 로컬 기록 → 로컬 알림.

인터넷 폴백: 열린 iPhone 앱 → Worker → 서명된 FCM NATIVE_PULL → Galaxy Queue 최대 6개 암호화 → KV 단기 암호문 → iPhone 복호화/저장 → ACK → FCM NATIVE_ACK → Galaxy Queue 완료.

명령은 BLE 또는 Worker/FCM으로 기존 원본 Notification contentIntent/cancelNotification/RemoteInput에 전달한다. 결과 상태는 새 알림 배너를 만들지 않는다. 원본이 사라졌으면 임의 앱 메인 화면을 열어 성공 처리하지 않는다.

Worker/KV/FCM을 확장했고 D1/R2/Durable Objects/VPS를 추가하지 않았다. PWA는 호환 경로로 유지하나 nativeMode에서 새 Galaxy 이벤트는 Web Push로 보내지 않는다. iOS에 aps-environment/remote-notification은 없다. 선택형 단절 위치 기록용 location 및 CallKit 수신 보고용 voip background mode를 선언한다.

## 인증과 키

QR: version 2, 임의 pairId/serviceUUID, 256bit bootstrap 비밀, 5분 만료. 서버 토큰/장기 키를 QR에 넣지 않는다. QR 화면은 Android FLAG_SECURE로 보호한다.

QR 또는 장기 linkKey HMAC-SHA256으로 ephemeral P-256 ECDH transcript를 상호 인증한다. 공개키는 65byte X9.63. HKDF-SHA256 salt는 인증 transcript, info는 GalaxyRelay BLE v2 session이다. 새 연결마다 새 ECDH 키로 AES-256-GCM 세션을 만든다.

HELLO: hello/pairId/clientNonce/clientPublicKey를 줄바꿈으로 연결.
WELCOME: welcome/pairId/clientNonce/serverNonce/clientPublicKey/serverPublicKey.
BLE AAD: GalaxyRelay/ble/v2 + pairId + 방향(android-to-ios 또는 ios-to-android).
12byte 임의 IV, 128bit tag, 방향별 AAD, 세션 replay ID 최대4096을 사용한다. 상한 뒤 재연결이 필요하다.

최초 CONFIG에 장기 linkKey/nativeCommandKey/native 전용 credential/HTTPS 주소를 암호화한다. iPhone Keychain 저장 후 READY를 보내면 Galaxy bootstrap을 폐기한다. 재연결은 장기 키로 새 ephemeral handshake를 한다.

4byte big-endian 길이+UTF-8 JSON을 20byte 조각으로 보내며 TX indication/RX write response, 최대64KiB/조립30초 제한이다. 저장 성공 후에만 ACK한다. 표준 암호 알고리즘이지만 프로토콜 조합은 자체 구현이며 외부 보안 감사를 받은 제품은 아니다. 개인용 1쌍에 맞춘 설계다.

## 서버 보관 정책 변경

원래 PWA는 본문/전체 기록을 KV에 저장하지 않는다. 무료 native foreground pull에서는 **암호문 전달 버퍼만 최대120초** 예외적으로 보관한다. ACK 시 삭제하며 미수신도 TTL 만료한다. 서버에는 키/원문/전체 기록을 넣지 않는다. 장기 기록은 Galaxy Room과 iPhone 파일에 있다.

Cloud AAD: GalaxyRelay/native-cloud/v2 + requestId + nativeDeviceId를 줄바꿈으로 연결. 키는 BLE로 전달된 linkKey. Worker는 키를 받거나 복호화하지 않는다. COMMAND_SECRET HMAC grant가 requestId/nativeId/androidId/expires를 묶어 다른 기기/만료 요청을 거부한다. 요청 메타데이터 KV write/negative cache를 줄이고 실제 암호문만 임시 보관한다. KV eventual consistency로 즉시 전달을 보장하지 않는다.

## API

| POST API | 역할 |
|---|---|
| /api/native/provision | Galaxy auth로 native credential 생성 |
| /api/native/pull/start | native auth, FCM pull/grant |
| /api/native/pull/upload | Galaxy auth+grant, ciphertext |
| /api/native/pull/result | native auth+grant, ciphertext 조회 |
| /api/native/pull/ack | native auth+grant, FCM ACK/버퍼 삭제 |
| /api/native/command | OPEN/DISMISS/PING/암호화 REPLY |
| /api/native/revoke | 관련 native token/pair 폐기 |

256bit 이상 Bearer token/hash KV, HTTPS only/no iPhone redirect, timestamp/nonce, rate limits, strict schema, 8KiB 일반/64KiB upload, HMAC/UUID/Room 명령 dedup을 적용한다. 본문을 서버 로그에 기록하지 않는다. 답장은 항상 암호화하며 기존 reply v1 AAD(commandId/eventId/timestamp/nonce/actionId/keyId)에 묶는다.

## 저장과 폐기

Galaxy는 Android Keystore, iPhone은 Keychain AfterFirstUnlockThisDeviceOnly 및 AES-GCM atomic file/iOS file protection을 사용한다. 기록5000, seen ID10000, tombstone5000, 명령256 상한. 플랫폼 저장소를 전체 Secure Enclave처럼 취급하지 않는다.

native revoke는 서버 credential을 폐기하고 Galaxy에 서명된 NATIVE_REVOKE를 보낸다. 오프라인 Galaxy의 로컬 키 즉시 폐기는 보장할 수 없으므로 Galaxy에도 연결 해제 기능을 둔다. 수동 해제는 인터넷 복구 후 실행하며, 서버 관계가 이미 없으면 로컬 키 폐기를 정상 완료한다. Android 최초 관리 등록/PWA 기존 관계와 native credential은 구분한다.

BLE는 Android connectedDevice foreground service/iOS bluetooth-central을 사용한다. 인터넷 폴백은 foreground HTTPS이며 위치 가장/WebSocket/잠금 우회/유료 APNs를 추가하지 않는다. Queue는 영속 저장 ACK 전 pending을 유지하지만 TTL 이후 재전달을 보장하지 않는다. 통화 audio/iMessage/앱 서버 읽음 강제 변경은 제외한다. iPhone 2.3.0은 선택형 CallKit 수신 및 통화 상태 표시, live BLE 원격 종료를 제공한다. Galaxy 전화 받기·음성 전달은 제공하지 않는다. CALL_HANGUP은 클라우드에 큐잉하지 않고 현재 단일 수신 callId·5초 TTL·권한·단일 SIM을 확인한다.

참고: [Apple BLE background](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html), [Apple 무료 계정](https://developer.apple.com/help/account/basics/about-your-developer-account), [Android BLE background](https://developer.android.com/develop/connectivity/bluetooth/ble/background), [KV consistency](https://developers.cloudflare.com/kv/concepts/how-kv-works/).

## 무료 한도

[Workers KV Free](https://developers.cloudflare.com/kv/platform/limits/)는 하루100000 reads/1000 writes 한도다. 암호문 버퍼 이외에도 replay nonce/auth 상태 write가 포함되므로 지속적인 인터넷 폴백 polling은 한도를 빨리 소진할 수 있다. BLE 우선/필요할 때 앱 열기·새로고침으로 사용하며 무료 한도 초과 시 전달이 실패할 수 있다. Paid 전환을 자동 설정하지 않았다. GitHub private macOS runner도 무료 사용량과 지출 예산을 확인한다.

## CallKit 수신 알림

인증된 BLE의 최신 실제 call 이벤트만 opt-in CallKit coordinator에 전달한다. 서버로 뒤늦게 받은 수신은 울리지 않는다. 일반 알림과 중복 표시하지 않으며 CallKit 보고 실패 시 유효한 현재 수신만 일반 알림으로 fallback한다. 순수 lifecycle 정책과 주입 가능한 provider를 분리해 중복·역순·단절·종료와 비동기 보고 경쟁을 테스트한다. 종료 신호, BLE 손실, provider reset, background task 만료 및 최대 45초 타이머로 알림을 닫는다. CallKit 보고용 voip 선언을 포함하며 PushKit·마이크·음성 session·추가 entitlement는 없다. [설정과 제한](call-alerts.md).
