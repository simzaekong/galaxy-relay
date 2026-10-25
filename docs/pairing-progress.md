# 2.0.2 QR 연결 진행 표시

양쪽 앱을 함께 업데이트한다. iPhone은 QR 인식 뒤 검색/발견/서비스 확인/보안 인증/서버 설정/최종 Galaxy 확인 단계를 표시한다. 최종 encrypted connected 수신 전에는 ready로 표시하지 않는다. 단계가 30초 이상 멈추면 검색/연결을 정리하고 새 QR과 Bluetooth/시간 설정 확인을 안내한다. iPhone QR 스캔은 햅틱으로 인식을 알려주며 카메라 준비 오류를 화면에 표시한다.

Android QR 창은 같은 pairId의 GATT 연결이 실제로 감지되면 자동으로 닫힌다. GATT 감지 자체는 인증 성공이 아니며 메인 화면에서 보안 연결 진행을 이어 표시한다. QR은 만료 카운트다운과 BLE 준비 상태를 표시하고 만료/새 pair로 교체 시 닫힌다. 닫힌 창의 감시 콜백은 제거한다. 양쪽이 서로의 암호화 세션 메시지를 확인한 뒤 연결 완료가 된다.

QR을 카메라로 읽은 사실만으로 Galaxy에서 이를 감지할 수는 없다. Galaxy는 실제 BLE 연결이 도착한 때 반응한다. iPhone에서 검색 중인데 Galaxy QR이 그대로면 아직 BLE 발견/연결이 되지 않은 상태다. QR 인식과 상호 BLE 연결은 별도로 검증해야 한다.

실제 두 휴대폰의 무선/카메라 검증은 물리 기기가 필요하다. 시뮬레이터 및 단위 테스트가 무선 연결 성공을 증명하지 않는다.

검증 결과: Android Debug/서명 Release build, JVM 7 tests, Debug/Release lint, 추가 QR UI 계측 테스트 compile 성공. Android UI 계측 실행은 이전 에뮬레이터 시스템 불안정으로 이번에는 수행하지 않았다. [iPhone CI 38741187211](https://github.com/simzaekong/galaxy-relay)에서 Release/Simulator build, 기존 프로토콜 4 tests와 앱 회귀 5 tests 및 실행 통과. IPA checksum/2.0.2 build 6 검증, APK 서명 검증 성공. 물리 BLE/QR 연결 확인은 남아 있다.


## Android 2.0.3 — QR이 즉시 닫히는 경로 수정

단순 GATT 접속 감지를 자동 닫기 조건으로 사용하지 않는다. 현재 QR의 인증 증명이 검증되고 세션 설정이 만들어진 경우에만 QR을 닫는다. GATT/광고/전송 콜백에는 생성 당시 세션 번호를 캡처하고 종료된 세션에서 늦게 돌아온 콜백은 무시한다. QR의 pairId도 생성 시점의 값으로 캡처한다.

QR 준비 중 반복 버튼 입력은 추가 작업을 만들지 않는다. QR이 만료되거나 다른 작업에서 폐기된 경우 창이 갑자기 사라지는 대신 해당 QR을 숨기고 재생성 안내를 표시한다. 이 경우 창은 사용자가 닫는다. iPhone 프로토콜을 바꾸지 않아 iPhone 2.0.2 IPA를 다시 빌드할 필요가 없다.

실제 사용자 기기 로그가 없으므로 이 증상의 단일 원인을 확정하지 않는다. 확인된 과도한 자동 닫기 조건과 이전 세션 콜백 경합을 수정한다.

2.0.3 검증: Debug/서명 Release APK 및 AndroidTest APK 컴파일 성공, JVM 테스트 10개(새 회귀 3개 포함) 통과, Debug/Release lint 오류 0개(기존 경고는 남음). 실제 Android QR 창/물리 BLE 계측 실행은 완료하지 못했으며 사용자 기기 확인이 필요하다.


## iPhone 2.0.4 — 인증 재시작 방지 및 원인별 안내

기존 보안 오류 메시지는 시간 차이뿐 아니라 GATT write 실패, 응답 조립 오류, 인증 증명 및 복호화 실패에도 공통으로 표시되어 원인을 구분할 수 없었다. B03~B15 코드와 원인별 사용자 안내를 추가하며 시간 차이가 실제 허용 범위를 벗어난 경우에만 자동 날짜·시간을 안내한다. 토큰/키/알림 내용은 진단에 포함하지 않는다.

이미 연결된 peripheral에 foreground 복귀가 발생하면 서비스 탐색과 알림 구독을 재시작하지 않는다. ClientHandshake는 같은 물리 연결에서 hello를 한 번만 생성하고 nonce/개인키를 유지한다. 중복 알림 구독 콜백은 새 인증 요청을 만들지 않는다. 무결성, timestamp, nonce, replay 검사는 그대로 유지한다.

실제 Android NativeCrypto로 생성한 Java 서버의 hello proof, welcome proof, P256 ECDH/HKDF 세션 키, AES-GCM 암호문을 Swift의 실제 ClientHandshake 코드가 검증하는 교차 테스트를 추가했다. tools/GenerateHandshakeFixture.java는 테스트 전용 키와 난수를 사용한 벡터 생성기이다. 실제 기기의 실패 원인은 구체적인 오류 코드 또는 기기 로그로 추가 확인해야 하며, 중복 인증 시작 경로를 수정했다는 사실과 물리 기기의 원인 확정은 구분한다.


진단 코드: B03 구독 시작 실패, B04 인증 요청 생성 실패, B05 응답 수신 실패, B06 프레임 조립 오류, B07 QR 인증값 불일치, B08 welcome 응답 상태/형식 오류, B09 공개키 확인 실패, B10 복호화 실패, B11 실제 시간 차이 초과, B12 중복 응답, B13 연결 데이터 형식 오류, B14 Galaxy write 거부/실패, B15 기타 연결 단계 실패. B14만으로 인증값 불일치나 시간 차이를 확정하지 않는다.

iPhone 2.0.4 검증: [CI 38745811375](https://github.com/simzaekong/galaxy-relay)에서 프로토콜 6개(실제 Java→Swift 인증/암호문 교차 검증 포함), 앱 회귀 5개, Release/Simulator build 및 실행 통과. 로컬에서 다운로드 archive/IPA SHA256, 2.0.4 build 8 확인. 물리 iPhone/Galaxy 실패 원인 확정과 무선 연결 검증은 남아 있다.


## Android 2.0.5 — B03 구독 단계 호환성

iPhone B03은 setNotifyValue의 완료 결과가 실패이거나 수신 구독이 활성화되지 않은 상태다. 인증 hello 이전 단계이며 날짜/암호화 실패를 뜻하지 않는다. Android에 빠져 있던 CCCD read 응답과 명시된 TX read 응답을 구현했다. CCCD는 기본 disabled 값, notify/indicate/양쪽/disable 설정을 지원하고 실제 선택한 모드에 맞춰 전송한다. 준비 쓰기, 길이, offset, 미지원 bit 요청에는 해당 GATT 오류를 반환하며 유효하지 않은 요청은 기존 구독을 변경하지 않는다.

현재 GATT 세션 안에서 유효한 구독 요청이 연결 상태 콜백보다 먼저 도착해도 같은 central을 한 번만 바인딩한다. 뒤늦은 connected 콜백이 이미 완료한 구독을 지우지 않는다. 이전 세션의 콜백 무시는 유지한다. 이는 무선 transport 연결 처리이며 QR 증명 검증/암호화/기기 인증을 생략하지 않는다. notify와 indicate 양쪽 모드에서도 전체 event의 애플리케이션 ACK 정책은 유지한다.

검증: Debug/서명 Release 및 AndroidTest APK compile, JVM 13 tests(구독 read/유효 모드/disable/잘못된 쓰기/offset 회귀 3개 포함), Debug/Release lint 통과. 실제 iPhone CoreBluetooth 연결 및 Android UI 계측 실행은 미검증이다. B03의 개별 NSError/무선 trace가 없어 단일 원인을 확정하지 않는다. iPhone 2.0.4는 유지하며 Android 2.0.5 설치 후 새 serviceUUID가 포함된 새 QR을 생성해 연결한다.

참고: [Android BluetoothGattServerCallback](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback), [Google/WebBluetooth peripheral sample](https://github.com/WebBluetoothCG/ble-test-peripheral-android/blob/master/app/src/main/java/io/github/webbluetoothcg/bletestperipheral/Peripheral.java), [Apple setNotifyValue](https://developer.apple.com/documentation/corebluetooth/cbperipheral/setnotifyvalue(_:for:)).

## Android 2.0.6 — B03 + G03 원인 구분용 진단

사용자 기기에서 B03과 G03이 함께 보고됐다. 구독 쓰기 거부 경로까지 확인됐지만 이전 G03은 조건을 구분하지 않아 단일 원인은 아직 확정할 수 없다. 인증 검사를 완화하거나 오류를 성공으로 처리하지 않는다. 이 버전은 연결 해결 완료판이 아니라 거부 조건을 확인하기 위한 진단판이다. iPhone 2.0.4는 유지한다.

| 코드 | 조건 |
| --- | --- |
| G03-D | CCCD UUID 또는 상위 TX characteristic 불일치 |
| G03-C | 이미 바인딩된 central과 요청 기기 불일치 |
| G03-P | 지원하지 않는 prepared write |
| G03-O | 잘못된 offset |
| G03-L | CCCD 값 길이가 2 byte가 아님 |
| G03-V | 지원하지 않는 구독 bit 값 |
| G03-R | Android sendResponse 호출 실패 |

거부 코드, descriptor/TX 일치 여부, peer 일치 여부, 준비 쓰기 여부, offset, 길이, 응답 요청 여부와 응답 API 결과만 로컬 저장한다. 주소, QR 비밀, 키, 인증 프레임, 알림 내용은 저장하지 않는다. 마지막 실패는 연결 해제 후에도 QR 화면 및 고급 → 블루투스 연결 진단에서 볼 수 있다. 새 QR 생성 또는 실제 연결 성공 시 지운다. Binder callback 값은 main thread로 넘기기 전에 복사하며 유효하지 않은 쓰기는 central을 새로 바인딩하지 않는다.

검증: Debug/서명 Release build, JVM 15 tests, Debug/Release lint 통과. 모든 65,536가지 2-byte CCCD 값에 대해 진단 판정과 실제 write 검증 일치를 테스트했다. 연결된 ADB 기기가 없어 물리 BLE는 실행하지 못했다. 위 코드와 진단 내용을 이용한 실제 기기 원인 확인이 남아 있다.

## Android 2.0.7 — G03-C 선점 및 연결 해제 처리

사용자 기기에서 G03-C가 확인됐다. 이는 기존 client와 CCCD write 요청자의 BluetoothDevice 비교가 불일치한 분기다. 기존 코드는 일반 STATE_CONNECTED 콜백 및 CCCD/TX read만으로 client를 선점하고, 인증 시간 초과 시 cancelConnection만 호출해 실제 disconnect 콜백 전까지 client를 유지했다. 기존 client가 어느 물리 기기였는지는 주소를 기록하지 않아 확인하지 못했다.

일반 연결 콜백과 read는 Relay 대상 기기를 선점하지 않는다. 유효한 Relay CCCD 활성화 요청에서만 대상 기기를 선택한다. 이 단계는 transport 준비이며 기존 QR HMAC 검증, ECDH 세션, AES-GCM 및 최종 연결 확인은 그대로 수행한다. 이미 구독 중인 다른 기기를 새 요청이 빼앗는 것은 허용하지 않는다. 다른 기기의 disabled CCCD 값 조회/해제는 기존 구독에 영향을 주지 않는다.

인증 시간 초과, 구독 해제, 인증 실패, 응답 API 실패 또는 전송 실패 콜백에서는 로컬 소유권과 세션을 즉시 지운다. OS disconnect 콜백 도착을 기다리며 잘못된 client가 남지 않는다. 연결 소유권마다 세대 번호를 발급해 같은 주소가 재연결되어도 이전 인증 타이머나 대기 전송 작업이 새 세션에 적용되지 않게 한다. 새 QR 및 GATT 서버 단위 세대 검증도 유지한다.

검증: Debug/서명 Release build, AndroidTest APK compile, JVM 19 tests, Debug/Release lint 통과. 추가 회귀 4개는 단일 peer 제한, 다른 peer의 해제 무시, 콜백 없이 시간 초과 해제 후 새 기기 수용, 동일 주소 재연결 후 이전 타이머 무효화와 reset을 확인한다. APK 서명과 소스 묶음의 비밀 제외 검증을 수행한다. 연결된 ADB 기기가 없어 물리 iPhone/Galaxy BLE 검증은 남아 있다. iPhone 2.0.4는 변경하지 않는다.
