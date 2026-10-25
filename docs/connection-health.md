# 대기 중 통신 확인 · Android 2.2.1 / iPhone 2.3.1

## 증상과 확인된 설계 문제

사용자는 약 9시간 지난 배터리 마지막 수신과 연결 표시 상태에서 전화 수신/받지 않은 전화 알림 누락을 보고했다. 당시 기기 로그가 없어 원인을 단정하지 않는다. 기존 배터리는 값이 바뀔 때만 보내므로 충전 완료처럼 값이 같으면 오래된 갱신 시각이 정상적으로 남는다. 그러나 기존 연결 표시는 마지막 인증 이후 실제 peer 응답을 계속 확인하지 않았다. 일반 큐의 응답 대기 뒤에 전화가 지연될 수도 있었고, 수신 후 종료 상태에는 별도 받지 않은 전화 알림이 없었다.

## 변경

- 인증된 양쪽에서 health_v1을 협상한다. Android foreground connectedDevice service가 기본 5분 간격으로 무작위 probe ID와 배터리를 포함한 암호화 health_ping을 보낸다. iPhone은 같은 ID와 자신의 배터리를 health_pong으로 답한다. 마지막 통신 확인과 배터리 갱신을 분리 표시한다. 기존 변경 기반 배터리 갱신도 유지한다.
- 응답은 현재 세션·probe ID와 일치해야 대기 상태를 해제한다. 응답이 30초 내 없으면 peer를 해제하고 기존 재연결 경로를 사용한다. iPhone은 앱을 다시 활성화할 때 최근 증거가 1분 넘게 오래됐으면 직접 확인 요청을 한다. background timer가 정시에 실행된다고 가정하지 않는다.
- OS Doze/iOS 중단이 타이머/이벤트를 지연시킬 수 있다. AlarmManager, wakelock, 위치, 상시 네트워크로 강제 실행하지 않는다. 따라서 5분/30초는 실행 가능한 동안의 정책이며 잠긴 실제 기기에서 절대 시간 보장은 아니다. 강제 종료·권한 철회·One UI의 강제 절전은 앱 코드로 모두 우회할 수 없다.
- 살아 있는 BLE가 확인되면 저장 큐를 다시 확인한다. 정상 이벤트 ACK 대기는 기존 45초에서 12초로 줄이고, ACK timeout은 peer 복구로 이어진다. 전화/명령 결과는 먼저 영속 저장하고 별도 우선 경로에서 3초 ACK를 기다린다. 일반 큐와 같은 이벤트가 겹치면 동일 receipt를 공유하여 응답을 서로 빼앗거나 건강한 연결을 잘못 끊지 않는다. 실패한 이벤트는 TTL 내 큐에 남긴다. 연결이 없을 때 계속 30초마다 깨우지는 않고 재연결 시 재시도한다.
- RINGING → IDLE에 unanswered 정보를 전송한다. iPhone은 최신 5분 이내 이벤트를 받으면 원래 ring이 없어도 받지 않은 전화 알림과 기록을 남긴다. 종료 알림 정리 콜백이 이 새 알림을 즉시 삭제하지 않도록 했다. 같은 event ID는 중복 표시하지 않는다. Galaxy 직접 거절과 진짜 부재중은 기본 PHONE_STATE로 구별할 수 없어 ‘부재중 확정’ 대신 ‘받지 않은 전화’라고 표시한다. iPhone 원격 거절은 해당 표시를 억제한다.

## 설치와 확인

양쪽 새 앱을 기존 앱 위에 설치하고 한 번씩 연다. 기존 QR/기기 인증은 유지한다. 두 앱이 새 capability를 협상하도록 재연결한다. iPhone 홈의 마지막 통신 확인과 배터리 갱신을 본다. 전화 상태 전달/READ_PHONE_STATE와 iPhone 알림 및 수신 화면 옵션은 기존대로 허용돼 있어야 한다.

Galaxy 고급 · 진단 및 보안 → 전화 수신 진단에서 최근 PHONE_STATE 수신 시각, 전달 설정, 권한을 확인할 수 있다. 기기 로그는 번호/본문 없이 시간·고정 상태만 보관한다. 블루투스 연결 진단의 health-timeout/event-ack-timeout은 당시 응답을 확인하지 못했다는 뜻이며 물리 무선 단절의 확정 진단은 아니다. health timeout 자체는 위치 조회를 시작하지 않는다.

실제 장시간 시험: 양쪽 화면 잠금 → 대기 → 다른 휴대폰에서 Galaxy로 전화 → 수신 화면 → Galaxy에서 받기/종료. 짧게 울리고 끊기, Bluetooth 끄기/켜기, 앱 다시 열기, One UI 절전 해제 조건도 비교한다. 이 야간 물리 시험은 자동 단위 테스트로 대체할 수 없다.


공식 근거: [Apple Core Bluetooth 백그라운드 처리](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html), [Android Doze 및 App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).


iPhone 2.3.2 표시 정리: 정상 연결 문구는 카드 상단의 초록색 ‘블루투스로 연결됨’ 하나만 표시한다. Galaxy 배터리 갱신 상대 시간은 해당 기기 칸 안에, 통신 확인 상대 시간은 가운데 연결 아이콘 아래에 시간만 표시한다. 연결 실패/연결 중 안내는 유지하며 VoiceOver는 각 시간의 의미를 읽는다. 통신·배터리 전송 주기는 바꾸지 않는다.
