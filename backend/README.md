# Backend

전체 설치 절차: [Cloudflare / Firebase guide](../docs/backend-setup.md).

`npm ci`, `npm run types`, `npm run check`, `npm test`, `npm run dev`.

PWA 빌드 후 `npm run deploy`. Cloudflare KV는 기기 상태만 저장한다. 모든 authenticated API는 `Authorization: Bearer ...`를 요구한다. POST에는 `timestamp`(현재 epoch ms)와 `nonce`(CSPRNG UUID)가 필요하다.

| API | 인증 / 역할 |
|---|---|
| GET /api/status | 인증 없으면 비민감 health, 있으면 해당 기기 상태 |
| GET /api/config | 공개 VAPID key |
| POST /api/android/register | INSTALL_SECRET + 기기 이름 + FCM token |
| POST /api/android/update | Android bearer, FCM token 갱신 |
| POST /api/pair/create | Android bearer |
| POST /api/pair/claim | 12자리 연결 코드, PWA 이름 |
| POST /api/pair/approve | Android bearer, 선택된 webId |
| POST /api/web/register | PWA bearer, subscription |
| POST /api/event | Android bearer, event |
| POST /api/state | Android bearer, 본문 없는 removed/command_result/call offhook·ended 상태 |
| POST /api/sync | PWA bearer, eventIds 최대 20 / commandIds 최대 8 / callIds 최대 10 |
| POST /api/command/open, dismiss, ack, ping | PWA bearer, commandId, eventId(핑 제외) |
| POST /api/settings | PWA bearer, commandId, autoDismiss |
| POST /api/ping | 해당 기기 bearer |
| POST /api/unpair | 연결 폐기, 양쪽 token 및 metadata 삭제 |

`/api/event`의 HTTP 200은 Push 서비스 접수다. command HTTP 200은 FCM 접수다. 실제 Galaxy 처리 결과는 `/api/state` → `/api/sync`로 확인하며 결과 배너를 띄우지 않는다. 기존 평문 control event도 상태 조회로 변환한다. 이전 APK가 암호화한 control Push는 Worker에서 종류를 구분할 수 없으므로 APK도 1.1.0으로 업데이트해야 한다. 인증 실패 401, 역할 실패 403, 입력 오류 400, 미연결 409, 만료 410, 크기 초과 413, rate limit 429, provider 실패 502, 일시 오류 503.

KV `status:{webId}:{event|command|call}:{id}`에는 엄격한 allowlist의 ID/종류/timestamp/state/result만 저장한다. 삭제·명령 결과 TTL 1시간, 통화 최종 상태 TTL 24시간. 원문/암호화 본문/번호/이름을 거부한다. `/api/sync`는 인증한 PWA와 승인된 peer의 상태만 조회한다. 연결 해제 시 해당 상태 키도 정리하며 KV 전파 지연·경쟁으로 남은 키는 TTL에 따라 만료된다.

`POST /api/command/reply`는 paired web 전용이며 `commandId`, `eventId`, timestamp/nonce와 `reply` object를 받는다. `reply`는 actionId/mode/plain text 또는 AES-GCM version/keyId/iv/ciphertext의 strict union이다. 전체 reply JSON을 HMAC으로 묶고 FCM으로 전달하며 KV에는 저장하지 않는다. 결과는 기존 `/api/state`·`/api/sync`로 조회한다. [프로토콜](../docs/reply.md).
