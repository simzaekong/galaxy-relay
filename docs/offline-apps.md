# iPhone 오프라인 전용 앱 · Android 2.3.0 / iPhone 2.4.0

## 설정

양쪽 새 앱을 기존 앱 위에 설치하고 한 번씩 열어 Bluetooth 재연결을 완료한다. QR을 바꿀 필요는 없다.

Galaxy Relay → 설정 → 알림 전달:

- 항상 전달할 앱: iPhone 네트워크와 관계없이 전달할 앱을 선택한다.
- iPhone 오프라인일 때만 전달할 앱: Instagram 등 추가로 받고 싶은 앱을 여러 개 선택한다. 이 목록에서 선택한 앱은 항상 전달 목록에서 자동 해제한다. 반대로 항상 전달로 선택하면 오프라인 전용에서 해제한다.
- 두 목록 모두 해제: 전달하지 않는다. 앱 전체 알림 전달 및 문자 전달 스위치는 그대로 적용한다.

예: Instagram을 오프라인 전용 목록에 넣으면 iPhone 네트워크 경로가 없는 동안 Galaxy의 새 Instagram 알림을 BLE로 전달하고, 네트워크가 돌아오면 새 전달을 멈춘다. 기존 iPhone 기록이나 Galaxy 원본 알림은 이 전환만으로 지우지 않는다. 꺼져 있던 앱의 과거 알림을 소급 수집하지 않는다.

iPhone 설정 → 연결 → 이 iPhone의 네트워크에서 온라인/오프라인/확인 중 상태를 볼 수 있다. 홈 카드에는 새 설명을 추가하지 않았다.

## 네트워크 상태의 의미

NWPathMonitor가 satisfied면 온라인, unsatisfied면 오프라인, requiresConnection/초기 상태면 확인 중이다. 이는 네트워크 경로의 상태이며 Instagram의 실제 push 수신 여부를 읽는 기능이 아니다. 로그인, APNs 지연, 집중 모드, 앱별 데이터 제한 등은 판별하지 않는다. Wi-Fi 경로가 있어도 외부 인터넷이 막힌 captive portal/공유기 WAN 장애는 온라인으로 분류될 수 있다. 별도 사이트를 주기적으로 호출하거나 전체 인터넷 트래픽을 감시하지 않도록 이 방식을 선택했다. [Apple NWPath.Status](https://developer.apple.com/documentation/network/nwpath/status-swift.enum/satisfied).

Bluetooth는 반드시 유지돼야 한다. iPhone이 오프라인이면 서버를 통한 iPhone 수신도 불가능하다. Galaxy는 해당 앱의 알림을 자체적으로 받을 인터넷 연결이 있어야 한다. iOS의 앱 중단/강제 종료와 무선 상태로 전환 전달이 지연되거나 불가능할 수 있어 즉시·무손실 수신을 보장하지 않는다.

## 전송과 안정성

- 기존 AES-GCM 세션에서 network_v1을 협상한다. iPhone 상태 변경, 연결 완료, 기존 health reply에 작은 online/offline/unknown 값만 전송한다. SSID, IP, 통신사 정보, 검색·접속 이력은 전송하지 않는다.
- 조건부 알림은 새로 캡처하기 전과 저장 큐에서 보내기 전에 iPhone에 UUID network_query를 보내 현재 경로를 재확인한다. 요청은 3초 상한, 같은 시점의 요청은 한 future를 공유하고 최대 1초 캐시로 연속 갱신을 합친다. 동일 notificationKey의 대기 알림은 최신 항목으로 합치며 대기 key는 최대 256개다.
- 상태 불명/오래된 상태/응답 없음/미지원 기기/연결 해제에서는 추가 앱 전달을 허용하지 않는다. 전용 이벤트가 전송 시점에 조건을 만족하지 않으면 대기 전달을 종료하므로, 온라인 복귀 후 뒤늦게 쏟아지지 않는다. 이 추가 알림은 조건 충족 시의 best-effort 전달이며 일반 항상 전달 알림과 동일한 오프라인 보존 보장을 제공하지 않는다.
- 설정 변경·기존 알림 제거/갱신을 캡처 직전에 재확인한다. 실제 BLE 쓰기 직전과 iPhone 저장/표시 직전에도 온라인 복귀를 차단한다. 알림 표시와 OS 네트워크 전환은 원자적 연산이 아니므로 아주 짧은 전환 경쟁까지 절대 보장하지는 못한다.
- offlineOnly로 표시한 이벤트는 Cloud pull에 포함하지 않고 기존 PWA/HTTP 경로로도 내보내지 않는다. 기존 알림 기록, 읽음/삭제/답장/원본 열기 기능은 유지한다.

## 실기기 확인

1. Instagram을 오프라인 전용에 선택한다. iPhone 온라인 + BLE 연결 중 새 Instagram 알림은 Relay로 안 오는지 확인한다.
2. iPhone Wi-Fi/셀룰러 데이터를 끄고 Bluetooth는 켜 둔다. Galaxy에는 인터넷을 유지하고 새 Instagram 알림을 발생시킨다.
3. Relay 수신과 원본 열기/삭제를 확인한다. 다른 앱도 여러 개 선택해 시험한다.
4. iPhone 네트워크를 복구한 뒤 새 알림이 Relay로 넘어오지 않는지 확인한다. 기존 기록은 남아 있어야 한다.
5. Bluetooth 해제, 초기 확인 중, 앱 선택 변경, 알림 폭주, iPhone 잠금/백그라운드에서도 비교한다. 실제 Instagram 전달과 iOS 경로 변경은 mock/CI만으로 검증하지 않는다.
