# Galaxy ↔ iPhone Relay 구조

Galaxy → HTTPS POST `/api/event` → Cloudflare Worker → VAPID Web Push → iPhone 홈 화면 PWA의 Service Worker → IndexedDB → 사용자 알림.

iPhone PWA → HTTPS `/api/command/{open,dismiss,ack,ping}` → Worker → Firebase HTTP v1 data message → Galaxy `FirebaseMessagingService` → HMAC·시간·commandId·nonce 검증 → 원본 Notification 조작 → `/api/state` → 임시 metadata → PWA `/api/sync` 조회.

## 저장 위치

| 위치 | 내용 |
|---|---|
| Worker KV | device metadata, token SHA-256 hash index, FCM token, Web Push subscription, pairing metadata, 설정, 만료되는 상태 receipt |
| Android Room | eventId ↔ notificationKey, Keystore AES-GCM으로 암호화한 로컬 이벤트·Queue, 명령 중복 방지 |
| iPhone IndexedDB | notifications(최대 5,000), 제거 tombstone, 중복 방지, 설정·PWA 인증 |
| Cache API | HTML/CSS/JS/아이콘만 |

KV의 device credential/peer, `push:{id}` subscription, `android:{id}` FCM token, `settings:{id}` 및 `heartbeat:{id}`를 분리했다. 설정·heartbeat 갱신이 오래된 device snapshot으로 최신 Push subscription을 덮어쓰지 않는다. 기존 metadata 형식에는 읽기 fallback을 제공하되 신규 저장은 분리된 키를 사용한다. 동일 필드에 대한 전역 원자성은 여전히 KV의 한계를 따른다.

Worker는 알림 본문·제목·번호·연락처 이름·전체 알림 기록을 저장하지 않는다. 상태 receipt는 해당 event/command/call ID와 시간·결과만 보관한다. 삭제·명령 결과 1시간, 통화 최종 상태 24시간 후 만료되며 다음 갱신으로 덮어쓴다. D1, R2, Durable Objects, Supabase, VPS, 상시 WebSocket을 사용하지 않는다.

선택형 E2EE를 켜면 Galaxy가 메시지·전화 발신자 이벤트를 AES-256-GCM으로 암호화하고 Worker는 불투명한 envelope만 Push한다. PWA Service Worker가 Web Crypto로 복호화·검증한 뒤 같은 IndexedDB 상태 흐름에 적용한다. 본문 없는 상태 receipt는 HTTPS 인증 채널의 metadata이며 E2EE 본문과 분리한다. 직접 기기 간 키 전달/지문 확인 및 신뢰 경계는 [보안 문서](security.md)에 설명한다.

## 인증·연결

1. Android가 INSTALL_SECRET으로 최초 등록하고 256-bit device token 및 별도 명령 검증 키를 받는다.
2. Android에서 12자리 CSPRNG 페어링 코드(5분)를 생성한다.
3. PWA가 코드를 claim하면 PWA 전용 256-bit token을 받는다. 아직 이벤트를 받을 권한은 없다.
4. Galaxy에서 PWA와 동일한 승인 코드를 직접 확인하고 승인한다.
5. 양쪽 `peer`가 서로 일치할 때만 이벤트·명령을 전달한다.

Cloudflare KV는 eventual consistency이고 compare-and-swap이 없다. 코드 순차 재사용은 차단하지만 여러 지역의 동시 claim에 대한 전역 원자적 1회 사용은 보장할 수 없다. Android의 최종 승인 및 양방향 peer 검사로 잘못된 claim이 자동 연결되는 것을 방지한다. 이 한계를 숨기는 KV counter나 유료 서비스는 추가하지 않는다.

## 원본 알림 열기

Galaxy에서 현재 활성 Notification의 `contentIntent`만 사용한다. PendingIntent는 서버에 직렬화하지 않는다. Relay 화면이 보이고 잠금이 해제된 경우만 즉시 실행을 시도한다. 그 외에는 사용자 확인용 Galaxy 로컬 알림을 띄운다. Background Activity Launch는 예외 없이 조용히 차단될 수 있으므로 백그라운드에서는 실행 성공을 추정하지 않는다.

`OPEN_SENT`는 PendingIntent에 실행 요청을 보냈다는 뜻이다. 원본 앱의 최종 화면·채팅방 이동 성공을 검사하는 API는 없다. 원본 알림이 사라지면 `NOT_FOUND`; 앱 Main Activity로 성공을 위장하지 않는다.

## 전달의 의미

HTTP 200은 Push 서비스가 요청을 수락한 상태다. 실제 iPhone 표시·영구 저장을 보장하는 전달 확인이 아니다. Push TTL 기본 1시간, Android 일반 알림 Queue TTL 1시간, SMS 24시간, 전화 수신 1분/종료 상태 24시간, 최대 retry 20회다. 이미 끝난 통화의 대기 수신 이벤트는 보내지 않는다. 서버에 본문 기록이 없어 iPhone의 장기 오프라인·저장 공간 삭제·Push 만료 후 서버에서 기록을 복구할 수 없다.

1.1.0부터 상태 변경·명령 결과는 Web Push로 보내지 않는다. PWA가 visible일 때 일반 15초/통화·명령 대기 중 5초 간격으로 bounded 조회하며 다시 열거나 새 실제 알림 Push를 받았을 때도 조회한다. 전화는 callId 하나로 수신 배너를 갱신하고 offhook/ended receipt를 받으면 `getNotifications()`의 해당 알림만 닫는다. IndexedDB에는 해당 통화의 기존 기록 상태만 바꾸며 별도 종료 알림을 추가하지 않는다.

WebKit은 사용자에게 보이는 Push를 요구한다. 따라서 새 배너 없이 완전히 닫힌 iPhone PWA를 항상 깨워 즉시 회수하는 기능은 제공할 수 없다. KV 전파 지연과 브라우저 알림 API 범위 내 best-effort다. 이전 APK의 암호화된 control Push는 분류할 수 없어 APK 업데이트 전까지 보이는 fallback이 유지된다.

## PWA → 원본 앱 답장

PWA 상세 form → authenticated `/api/command/reply` → reply JSON을 포함한 HMAC-signed FCM data → Android UUID/nonce claim → 현재 Notification/RemoteInput/action identity 재검사 → original PendingIntent.send(fill-in RemoteInput). 결과는 content-free `/api/state` receipt → PWA `/api/sync`로 조회하며 status Push를 만들지 않는다. 선택 E2EE를 켜면 reply ciphertext만 Worker/FCM으로 보낸다. [wire format·제약](reply.md).
