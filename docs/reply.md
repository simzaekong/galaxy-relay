# PWA 알림 답장

## 설치와 사용

1. Galaxy에 `artifacts/GalaxyRelay.apk` **1.2.0 / versionCode 3**을 기존 앱 위에 업데이트 설치한다. 기존 앱을 삭제하면 연결·키·로컬 기록을 잃을 수 있다. 릴리스 서명 키는 기존 버전과 같다.
2. 홈 화면 Relay PWA를 완전히 닫았다가 다시 실행해 최신 Service Worker를 적용한다. 연결을 새로 만들 필요는 없다.
3. Galaxy의 알림 접근 권한과 해당 앱 전달 설정을 켠다. **업데이트 후 새로 도착한 알림**에서 시험한다. 예전 APK가 보낸 기록에는 답장 가능 정보가 없다.
4. 묶음을 펼치고 개별 최신 알림을 눌러 상세를 연다. 원본 알림이 답장을 지원하면 상세 카드 아래 입력창이 나타난다.
5. 1~500자 답장을 입력하고 **답장 보내기**를 누른다. 줄바꿈·한국어·이모지를 지원한다. 입력창 클릭이나 전송 버튼은 상세 카드 재탭의 ‘Galaxy에서 열기’를 실행하지 않는다.
6. Galaxy 응답이 오면 “Galaxy의 원본 앱에 답장을 전달했습니다.”를 표시한다. 결과 알림을 별도로 Push하지 않는다. 원본 앱이 알림을 유지하면 PWA 기록도 유지하며, 앱이 알림을 제거하면 기존 제거 동기화로 정리한다.

## 지원 조건

- Android 12/API 31 이상에서만 답장을 제공한다. 원본 PendingIntent의 공개 타입·mutable 검사 API가 Android 12부터 제공되어 그 이전에는 안전한 검증을 할 수 없다는 이유다. 기존 알림 전달의 minSdk 26은 유지한다.
- 원본 앱이 활성 알림에 **자유 입력 RemoteInput + mutable Broadcast/Service PendingIntent**를 제공해야 한다. 원본 앱 소유 Intent인지 확인한다. Activity 동작, immutable Intent, 선택지만 있는 응답, 여러 자유 입력 필드가 필요한 동작은 지원하지 않는다. 여러 답장 액션 중 명시적 REPLY를 먼저 선택한다.
- 카카오톡/Discord/문자/Instagram이라도 알림별·앱 버전별 답장 지원은 다를 수 있다. 해당 Galaxy 알림에 원래 답장이 없다면 PWA에서도 생성하지 않는다. 앱 내부 프로토콜이나 채팅방 Deep Link를 추측하지 않는다.
- **Galaxy 잠금 해제**가 필요하다. 잠긴 경우 `REPLY_REQUIRES_UNLOCK` 결과를 보여준다. 인증이 필요한 원본 동작이나 잠금화면을 우회하지 않는다. 화면을 반드시 Relay 앱에 유지할 필요는 없다.
- SMS 직접 수신 이벤트 자체에는 답장 Intent가 없다. 문자 답장은 Samsung/Google Messages의 원본 알림 전달을 사용한다. `SMS 직접 수신`을 켜면 문자 앱 알림 대신 수신 이벤트만 전달하므로 필요하면 해당 옵션을 끈다. Relay가 `SEND_SMS` 권한을 추가로 요구하지 않는다.
- 이전 갱신 기록과 제거된 기록, 오프라인에서는 답장을 비활성화한다. 실행 직전 현재 활성 원본의 postTime과 액션 지문을 다시 비교한다. 알림 접근 재연결 때는 활성 알림을 다시 수집해 누락을 보완한다. Intent가 바뀌거나 취소되었으면 실패 상태를 표시하며 앱만 열기로 성공 처리하지 않는다.
- `REPLY_SENT`는 원본 앱의 PendingIntent 실행 성공을 뜻한다. 상대방 수신, 앱 서버 처리, 읽음 확인을 보장하는 상태는 아니다. 원본 앱의 네트워크·계정 상태에 따라 이후 전송이 실패할 수 있다.
- iPhone **PWA 상세 화면 안에서** 답장한다. iOS 시스템 Push 배너 자체에 네이티브 답장 입력창을 추가하는 기능은 아니다.

## 보안과 재전송

`POST /api/command/reply`는 기존 Bearer 인증·기기 역할·pair 검증·rate limit·8192-byte 요청 제한을 적용한다. 이벤트 UUID, 명령 UUID, timestamp, nonce 및 reply JSON 전체를 HMAC으로 서명한다. 기존 OPEN 등의 서명 형식은 유지한다. FCM data JSON은 3500 bytes 이하, plaintext는 500 UTF-16 code units/1500 UTF-8 bytes 이하로 제한한다.

Android는 명령 ID와 nonce를 Room에 원자적으로 claim한 뒤 한 번만 실행한다. PWA는 전송 직전 이벤트별 대기 명령 ID와 시각만 IndexedDB에 저장하여 빠른 연속 탭·다른 탭·새로고침에 의한 같은 알림의 중복 요청을 막는다. 대기는 최대 5분이며 답장 본문은 저장하지 않는다. 전송/수신 경계에서 기기 프로세스가 종료되거나 응답이 유실된 경우 완전한 exactly-once/성공 확인을 보장하지 않는다. 자동 재전송은 하지 않으며, 결과가 불확실하면 Galaxy 원본 앱에서 전송 여부를 확인한 후 재시도한다. 사용자 재시도는 새 명령이므로 기존 답장이 실제 전송됐다면 중복 메시지가 될 수 있다.

서버 KV에는 답장 내용·암호문을 저장하지 않는다. 실행 receipt에는 명령 ID·대상 이벤트 ID·결과·시각만 임시 저장한다. 답장 내용을 로그나 Push 배너에 기록하지 않는다. HTTPS 기본 모드에서는 Worker/FCM을 거칠 때 평문 내용을 처리할 수 있다. E2EE를 사용할 경우 아래 형식으로 서버에 원문을 전달하지 않는다.

## 답장 E2EE

Galaxy에서 생성한 기존 AES-256 키를 PWA에 직접 전달하고 지문을 비교한다. 최신 PWA는 non-extractable Web Crypto key를 encrypt/decrypt 권한으로 가져온다. 예전 PWA가 저장한 **decrypt 전용 non-extractable 키**는 권한을 변경할 수 없으므로 Galaxy의 **같은 키를 한 번 다시 저장**해야 한다. 재입력하기 전 평문으로 자동 전환하지 않는다. Galaxy에서 E2EE 전송을 켠 경우 평문 REPLY도 거부한다. 키를 새로 생성하면 기존 양쪽 키 설정을 다시 맞춰야 하므로 단순 재저장은 기존 키로 한다.

AES-GCM 표준과 무작위 96-bit IV를 사용한다. 각 답장의 additional authenticated data는 다음 문자열이다.

```text
GalaxyRelay/reply/v1
commandId
eventId
timestamp
nonce
actionId
keyId
```

알림 방향의 `GalaxyRelay/v1`과 다른 domain prefix를 사용하고, 명령·대상·nonce·액션·키 지문을 암호문에 묶는다. FCM의 HMAC 서명도 암호문·IV·mode 전체를 별도로 인증한다. 답장 원문은 UTF-8 text이며 사용자 임의 RemoteInput key나 Intent는 전송하지 않는다. Android에는 원본 앱의 답장 전달 순간 평문이 필요하다. 이 기능이 원본 메신저의 암호화나 계정 인증을 대체하지 않는다.

## 검증 근거

[Android RemoteInput 공식 API](https://developer.android.com/reference/android/app/RemoteInput), [Notification.Action](https://developer.android.com/reference/android/app/Notification.Action), [FCM data messages](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type)를 따른다. 실제 실행 결과와 미검증 범위는 [test-results.md](test-results.md)에 기록한다. 별도 test-fixture APK는 실제 연락처로 메시지를 보내지 않고 수신한 합성 RemoteInput 텍스트만 자체 private storage에 기록한다. 이 receiver는 Relay 배포 APK에 포함하지 않는다.
