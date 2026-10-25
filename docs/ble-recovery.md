# 장시간 BLE 연결 복구 · Android 2.0.9 / iPhone 2.1.1

## 확인된 코드 결함과 원인 미확정 범위

사용자는 처음에는 동작하지만 시간이 지나 Galaxy가 먼저 끊기고 자동 재연결되지 않는다고 보고했다. 물리 기기 연결 로그가 없어 최초 단절이 One UI 절전, 거리, OS Bluetooth stack, 전송 실패 중 무엇인지는 확정하지 않는다. 다음 복구 누락은 실제 코드에서 확인했다.

- Android는 START_NOT_STICKY여서 프로세스 종료 후 시스템 재생성 대상이 아니었다. Bluetooth OFF 수신 시 서비스 자체를 종료해 ON 전환도 듣지 못했다.
- GATT 전송 API 실패 시 ready=false만 설정하고 peer/전송 대기를 남겼다. onNotificationSent 콜백이 없으면 indicating=true가 영구 유지됐다. 재광고 복구 및 일시적 서비스/광고 준비 실패 재시도도 없었다.
- iPhone의 검색/연결 timeout은 기존 연결 복구도 stop()으로 처리해 identity와 재시도 활성 상태를 지웠다. 일시적 B03/B05/B06/B14 오류도 모든 자동 복구를 중단했다.
- 양쪽 message ID 집합은 세션 전체에서 무한 누적한 뒤 4,096개를 넘으면 연결을 실패 처리했다.

## 변경

Android connectedDevice foreground service는 nativeMode/기존 pair가 유효한 동안 START_STICKY를 반환한다. Bluetooth OFF 때 foreground 대기로 전환하고 ON 때 GATT/광고를 다시 준비한다. 일시적 stack/서비스/광고 실패는 1/5/15/30/60초 상한 backoff로 재시도한다. 권한 취소 및 사용자 연결 해제는 재시도하지 않는다. 별도 Alarm, 위치 기능, WakeLock, 상시 heartbeat는 추가하지 않는다.

peer 해제, 인증 timeout, 전송 오류 후 로컬 상태를 즉시 초기화하고 광고를 다시 시작한다. 서버 세션/peer/광고 시도/전송마다 세대 번호를 확인해 이전 callback/타이머가 새 연결을 끊지 않게 한다. 한 전송 callback이 15초 안에 오지 않으면 해당 연결을 복구한다. 앱 ACK 대기는 45초이며 ACK를 받지 못한 이벤트는 Queue에 남긴다. 발신 암호화/프레임 생성은 main handler에서 직렬 처리한다.

Android는 기기별 협상 MTU에서 최대 185 byte까지 사용하고 협상이 없으면 기존 20 byte로 전송한다. iPhone 수신 프레임 형식은 바꾸지 않는다. 아이콘 이벤트가 백그라운드에서 수백 개 20-byte 전송으로 길어지는 부담을 줄인다.

iPhone은 새 QR 연결과 이미 검증된 pair의 복구를 구분한다. 기존 pair는 검색 및 OS pending connection에 30초 종료 제한을 두지 않는다. 확인한 peripheral identifier를 해당 pairId와 연결해 저장하고 retrievePeripherals/connect를 사용한다. Bluetooth가 켜지면 다시 연결하며 state restoration 시 복구한 peripheral도 실제 서비스 탐색/인증을 다시 수행한다. 짧은 transport 실패는 1/5/15/30/60초 backoff, 연결 해제 완료 대기는 최대 10초 뒤 재시도한다. 실제 사용자 stop/unpair는 모든 재시도를 취소한다.

QR HMAC, ECDH, AES-GCM, timestamp 및 중복 검증은 유지한다. 복호화까지 성공했으나 freshness를 벗어난 B11 패킷도 적용하지 않고 기존 pair의 연결만 재설정한다. 암호 증명/공개키/복호화/실제 replay 오류는 기존대로 중단한다. message ID는 timestamp+120초까지 유지하고 그 이후에만 제거한다. 유효 ID 4,096개가 동시에 차면 기존 ID를 강제 퇴출하지 않는다. iPhone은 세션을 다시 협상하며 Android도 해당 연결을 거부 후 복구한다. 오래 사용했다는 이유로 과거 ID 누적 수에 도달하지 않는다.

## 진단과 실기기 검증

Galaxy 고급 → 블루투스 연결 진단에 최근 16개 시각/사유(service-start/restarted/destroyed, adapter-off/on, link-disconnected-status, notify-timeout/rejected, authenticated 등)를 로컬 저장한다. 주소, 토큰, QR, 키, 알림 본문은 기록하지 않는다. 강제 종료는 onDestroy가 오지 않을 수 있어 service-destroyed 부재가 정상 종료를 증명하지 않는다.

업데이트 후 앱을 양쪽에서 한 번 열고 기존 pair를 유지한 채 다음을 확인한다.

1. 두 기기 화면을 잠근 채 30분 이상 알림 전달 후 잠금 해제.
2. Galaxy Bluetooth OFF → 1분 후 ON → QR 없이 재연결.
3. 범위를 벗어나 2분 이상 대기 → 복귀 → QR 없이 재연결.
4. 짧은 연결/전송 오류 후 Queue 전달 및 중복 알림 방지.
5. 사용자 연결 해제 후 자동 연결이 다시 시작되지 않음.

One UI 강제 절전/앱 강제 중지 및 iOS 사용자의 앱 밀어 종료는 OS가 복구를 막을 수 있다. 정상 사용은 양쪽 앱을 강제로 종료하지 않고 Galaxy 설정 → 앱 → Galaxy Relay → 배터리에서 제한 없음을 선택한다. START_STICKY는 OEM 강제 종료까지 우회하는 보장이 아니다. 실제 장시간 화면 잠금/거리 복구는 시뮬레이터로 증명할 수 없다.

## 실행 결과 (2026-10-16)

- Android Debug/서명 Release build, JVM 24개 통과, Debug/Release lint 오류 0개(기존 경고 27개 유지), release APK v2 서명 확인.
- [iPhone CI 38874542111](https://github.com/simzaekong/galaxy-relay) 성공. protocol 9개, hosted app 12개(DisconnectTests 8개 포함), iPhone/Simulator build와 실제 simulator 실행 통과.
- 반복된 paired timeout에도 자동 복구 유지, 명시적 stop/unpair 후 복구 중단, fresh QR 실패의 기존 보안 처리, 10,000개 장기 메시지 처리, 미래 timestamp replay 보호, 유효 replay 집합 포화 시 보안 유지, MTU 프레임 재조립을 검증했다.
- 내려받은 artifact ZIP/IPA의 SHA256, ZIP 무결성, iPhone 2.1.1 build 10 / 기존 bundle ID / 최소 iOS 16.0 확인. 소스 및 APK의 로컬 비밀 제외 검사 통과.
- ADB 서버 연결이 되지 않아 실제 Galaxy 서비스 재시작/잠금 후 장시간 동작을 자동 검증하지 못했다. 무선 거리/절전/화면 잠금 실기기 시나리오와 최초 단절의 확정 원인은 남아 있다.

참고: [Android Service](https://developer.android.com/reference/android/app/Service#START_STICKY), [BLE background](https://developer.android.com/develop/connectivity/bluetooth/ble/background), [Apple background and pending connections](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html).
