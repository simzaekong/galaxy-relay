# 양방향 배터리 · Android 2.0.10 / iPhone 2.1.2

양쪽 홈의 연결 영역에 Galaxy와 iPhone 각각의 잔량/충전 상태를 표시한다. 상대 값에는 마지막 수신 시각을 표시하며 연결이 끊겼을 때 실시간 값으로 가장하지 않는다. 같은 pairId의 마지막 수신만 복원하고 새 pair에는 이전 기기 값을 표시하지 않는다.

## 전력 정책

- Bluetooth의 기존 인증/암호화 세션이 준비되면 한 번씩 교환한다.
- 잔량 변화는 마지막 전송에서 최소 5분, 충전 상태 변화는 최소 1분 간격으로 최신 값 하나로 합친다.
- 값이 같으면 시간만 지났다는 이유로 전송하지 않는다. 값이 다시 이전 값으로 돌아오면 예약 전송도 취소한다.
- Android ACTION_BATTERY_CHANGED와 iOS UIDevice battery notifications를 사용한다. 잔량 확인을 위한 주기적 네트워크 요청/Heartbeat/WakeLock/Alarm을 추가하지 않는다. 화면 복귀 때 로컬 값을 한 번 갱신한다.
- 예약 전송은 연결 중 변경이 있을 때만 존재한다. 연결이 해제되면 취소하며 재연결 때 최신 값으로 다시 교환한다. OS가 앱을 suspend하면 전송이 더 늦을 수 있다.
- 서버/KV에는 배터리 조회를 위한 요청·주기 쓰기를 추가하지 않는다. Bluetooth가 끊긴 동안에는 마지막 수신 값을 표시한다.

## 호환성과 검증

config와 ready에 battery_v1 capability를 추가한다. 양쪽에서 지원을 확인해야 device_status를 보내므로 업데이트 전 버전과 잠시 섞여도 알 수 없는 메시지로 연결을 끊지 않는다. payload는 level 0~100 또는 unknown, charging/full/plugged/unplugged/unknown 상태와 기존 암호화 timestamp/message ID만 사용한다. 배터리는 알림 기록이나 알림 배너로 표시하지 않는다.

단위 테스트는 잦은 변화 합치기, 변화 없는 무전송, 충전 전환 1분 제한, 재연결 최초 교환, 시계 역행 시 과다 전송 방지, 잘못된 값 거부, 이전 PairSettings 호환 및 다른 pair의 캐시 미표시를 확인한다. 실제 iPhone 화면 잠금 후 OS 배터리 이벤트 전달 시점과 배터리 소모량은 실기기 측정이 필요하다. 단위 테스트 통과가 실제 소모량 절감을 수치로 입증하지는 않는다.

2026-10-16 검증: Android Debug/서명 Release build, JVM 28개, Debug/Release lint 오류 0개(경고 29개) 통과. [iPhone CI 38879964839](https://github.com/simzaekong/galaxy-relay)에서 protocol 16개, hosted app 13개, iPhone/Simulator build 및 simulator 실행 통과. 다운로드 ZIP/IPA SHA256 및 무결성, iPhone 2.1.2 build 11 / 기존 bundle ID / 최소 iOS 16.0, Android APK v2 서명 확인. 시뮬레이터 홈 화면에서 배터리 미수신 상태의 대시 표시를 확인했다. 시뮬레이터는 실제 양방향 잔량 교환이나 전력 소모를 증명하지 않는다.

## 위치 질문에 대한 설계 기준

Always 권한과 연속 측정은 별개다. 필요할 때 단발 요청을 시작하고 결과 또는 짧은 timeout 후 중지하는 방식으로 구현할 수 있다. 최근 위치에 측정 시각·정확도를 함께 표시하고 필요 시에만 새 위치를 요청하는 것이 합리적이다. 높은 정확도와 빈번한 갱신을 줄이면 연속 고정밀 측정보다 전력 사용을 줄일 수 있지만 환경에 따라 달라진다.

권한만으로 정지/종료된 iPhone 앱을 임의의 서버 요청으로 언제든 깨울 수는 없다. 요청이 Bluetooth 연결 또는 실행 중인 앱에 도착할 수 있어야 하며 Android/iOS의 background execution 및 위치 정책을 함께 충족해야 한다. 위치 권한을 알림 연결 유지 수단으로 사용하지 않는다. 이번 배터리 변경에서는 위치 권한·위치 측정을 추가하지 않는다.

참고: [Apple requestLocation](https://developer.apple.com/documentation/corelocation/cllocationmanager/requestlocation()), [Apple location authorization](https://developer.apple.com/documentation/bundleresources/choosing-the-location-services-authorization-to-request), [Android location battery](https://developer.android.com/develop/sensors-and-location/location/battery).
