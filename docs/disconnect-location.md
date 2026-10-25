# Bluetooth 연결 해제 위치 · 2026-10-16

Android 2.0.11 (15), iPhone 2.1.3 (12).

## 동작

- 인증이 완료된 BLE 세션이 끊어졌을 때만, 각 기기가 **자기 위치**를 조회한다. iPhone 기록은 Galaxy의 실시간 좌표가 아니라 iPhone이 단절을 감지한 곳이다. 단절을 감지하는 시간도 OS마다 다르다.
- 3초 동안 재연결되면 취소한다. 이후 위치 요청은 한 번, 최대 15초로 종료한다. 재연결·권한 취소·기능 끄기·앱의 연결 해제·기록 삭제 시 진행 중인 요청도 취소한다.
- 요청 시도 간 최소 5분을 둔다. 재시도 검색 자체, 초기 QR 인증 실패, 앱 시작, 권한 허용, 화면 열기에는 위치를 읽지 않는다. 수동 연결 해제는 기록하지 않는다.
- 약 100m 정확도를 목표로 한다. 대략적 위치도 허용하며 정확도를 표시한다. 유효하지 않은 좌표, 음수 또는 10km 초과 정확도, 단절보다 30초 이상 오래된 값, 미래 값은 거부한다. 수신할 수 없으면 이전 기록을 덮어쓰지 않는다.
- 최신 기록 한 개만 유지한다. 단절 시각과 실제 측정 시각을 별도로 저장한다. 이전 기록은 날짜·정확도와 함께 표시하며 ‘현재 위치’로 표시하지 않는다.
- 홈의 지도 버튼은 이미 저장한 좌표를 지도 앱에 넘긴다. 지도 조회로 새 위치를 측정하지 않는다.

## 권한 설정

### Galaxy

Android 2.1.0 이상 새 APK를 기존 앱 위에 설치한다. 홈 → 마지막 연결 해제 위치 → 위치 권한 설정, 또는 설정 탭 맨 위의 연결 해제 위치에서 ‘연결 해제 시에만 위치 기록’을 켜고 ‘연결 해제 위치 권한 설정’을 누른다. 먼저 사용 중 위치를 허용한다. 같은 버튼을 다시 눌러 앱 설정 → 권한 → 위치 → **항상 허용**을 선택하면 화면이 꺼진 상태에서도 시도한다. Android 10은 별도 배경 위치 권한 창, Android 8/9는 기본 위치 권한으로 처리한다. 대략적 위치를 선택해도 된다.

기존 BLE foreground service는 connectedDevice 타입만 사용한다. 측정하는 동안에만 location 타입을 추가하고 결과·실패·15초 제한 후 제거한다. 기존 연결 서비스는 유지한다. 위치 권한을 거부해도 BLE·배터리·알림 기능을 사용할 수 있다.

### iPhone

AltStore에서 새 IPA를 기존 앱 위에 설치한다. Relay 설정 → 연결 해제 위치 → 위치 권한 설정을 누른다. 먼저 앱 사용 중 허용 후 버튼을 다시 눌러 항상 허용을 요청한다. iOS가 변경 안내만 표시하면 시스템 설정 → 개인정보 보호 및 보안 → 위치 서비스 → Galaxy Relay에서 **항상**을 선택한다. 권한 변경은 측정을 시작하지 않는다.

Core Location requestLocation 1회 요청과 location background mode를 사용한다. BLE 단절 처리에 한정된 짧은 background task를 사용하며 끝나면 종료한다. 상시 startUpdatingLocation, significant location, geofence, 위치로 BLE 유지, 주기적 위치 갱신은 사용하지 않는다.

## 개인정보와 제한

좌표는 Android Keystore AES-GCM / iOS Keychain에 최신 한 개만 저장한다. 서버·BLE·FCM·로그로 전송하지 않는다. 홈 지도 버튼을 직접 누르면 외부 지도 앱에 좌표가 전달된다. 설정에서 기록 삭제가 가능하고 수동 페어링 해제 시 로컬 기록을 삭제한다. 서로 다른 pair의 기록은 표시하지 않는다.

항상 허용은 위치 조회 권한이며 무제한 백그라운드 실행 보장은 아니다. 앱 강제 종료·프로세스 종료·제조사 절전·GPS 불가·iOS 실행 시간 제한 시 기록하지 못할 수 있다. 상시 추적을 하지 않으므로 OS가 앱을 깨우지 않은 동안의 단절을 나중에 소급 측정하지 않는다. 연결 중 위치를 저장하지 않으므로 ‘마지막으로 연결되어 있던 순간’의 정밀 GPS를 보장하지 않는다. 실내·대략적 위치는 오차가 클 수 있다.

## 실기기 인수 절차

1. 두 기기 위치를 항상 허용하고 Relay BLE 연결 완료를 확인한다. 연결 중에는 Relay의 위치 사용 표시가 계속 켜지지 않아야 한다.
2. 화면을 켠 상태에서 상대 Bluetooth를 끄고 약 18초 이내 기록/실패 상태와 날짜를 확인한다. 지도 버튼으로 위치를 확인한다.
3. 화면을 잠근 상태에서 같은 시험을 한다. 전 시험과 최소 5분 간격을 둔다.
4. 재연결이 3초 내 이루어진 경우, 5분 내 반복 단절, QR 인증 실패, 수동 연결 해제, 위치 권한 거부에서는 새 좌표를 쓰지 않아야 한다.
5. 위치 기록 삭제 후 재시작해도 복원되지 않아야 한다. 앱 알림/배터리/재연결도 회귀 확인한다.

물리 기기의 GPS·잠금 상태 시험과 배터리 소모 계측은 아직 수행하지 않았다. 빌드·자동 검증 결과는 native-tests.md에 별도로 기록한다.

공식 참고: [Apple 위치 권한](https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services), [Apple 일회 위치 요청](https://developer.apple.com/documentation/corelocation/cllocationmanager/requestlocation()), [Android 배경 위치 권한](https://developer.android.com/develop/sensors-and-location/location/permissions/background), [Android foreground service 타입](https://developer.android.com/develop/background-work/services/fgs/service-types#location).
