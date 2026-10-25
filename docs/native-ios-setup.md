> 공개 스냅샷 안내: 과거 배포·빌드 기록입니다. 운영 주소와 다운로드는 제공하지 않으며, 현재 상태와 설치 전제는 [공개 설정 안내](public-source.md)를 먼저 확인하세요.

# 네이티브 Relay 설치 · Windows + 무료 Apple Account

2026-10-17 / Android 2.2.1 · iPhone 2.3.2. GitHub macOS Actions가 unsigned IPA를 만들고, Windows AltStore Classic/AltServer가 본인의 무료 Apple Account로 서명한다. 본인 소유 Mac이나 유료 Apple Developer Program은 필요하지 않다. GitHub에 Apple 비밀번호/인증서를 넣지 않는다.

## Galaxy 준비

1. artifacts/GalaxyRelay.apk를 **덮어 설치**한다. 기존 설치와 같은 개인용 Release 키를 사용한다. 삭제하면 등록과 로컬 키가 사라진다.
2. 기존 Backend 등록은 유지한다. 새 설치라면 [Android 설정](android-setup.md)에 따라 먼저 등록한다. 주소는 https://relay.example.com 이다. 최초 설치 비밀값은 Galaxy에서만 입력한다.
3. 알림 접근/Relay 알림 표시를 허용하고 전달할 앱과 알림 전달을 켠다. 전화/SMS 권한은 필요한 기능만 허용한다.
4. Bluetooth를 켜고 **QR로 iPhone 연결** → 근처 기기 권한 허용 → 다시 누른다. 최초 QR 생성에는 Galaxy 인터넷/Backend 등록이 필요하다.
5. QR은 5분 유효하며 연결 인증 비밀이 들어 있으므로 캡처하거나 공유하지 않는다.

BLE 동안 Galaxy에 저중요도 연결 서비스 알림 하나가 표시된다. OS가 서비스를 자주 종료하면 Galaxy 배터리 제한을 완화한다. 연결 유지를 위해 위치를 추적하지 않는다. 선택형 연결 해제 위치 기록은 별도 위치 권한을 사용하며 단절 때만 한 번 조회한다.

## iPhone 설치

1. [공식 Windows 안내](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows)에 따라 AltServer와 안내된 iTunes/iCloud 배포본을 설치한다.
2. USB로 iPhone 연결 → 이 컴퓨터 신뢰 → AltServer Install AltStore에서 기기 선택 → 본인의 무료 Apple Account로 설치. Apple 인증 정보는 AltServer에만 입력한다.
3. 설정 → 일반 → VPN 및 기기 관리에서 개발자 앱을 신뢰한다. iOS 16 이상에서 요구하면 개인정보 보호 및 보안 → 개발자 모드를 켜고 재시작한다.
4. artifacts/GalaxyRelay-unsigned.ipa를 iPhone 파일 앱에 가져와 AltStore My Apps → **+**에서 선택한다. ZIP이 아니라 내부 IPA를 선택한다.

**7일 만료 전에 AltStore에서 Refresh**한다. AltServer 실행 중인 PC에 USB 연결하거나 공식 안내에 따라 같은 네트워크/Wi-Fi 동기화를 사용한다. 매주 GitHub 빌드나 APK 재설치는 필요 없다. 동일 Apple Account/앱을 유지하고 앱을 삭제하지 않는다. 무료 앱 수/기기 등록 제한은 Apple 정책을 따른다. [갱신 안내](https://faq.altstore.io/altstore-classic/your-altstore).

## QR 연결

iPhone Relay 실행 → **Galaxy QR로 연결** → 카메라/Bluetooth/알림 권한 허용 → Galaxy의 새 QR 스캔. 인증된 BLE로 서버 주소, 전용 토큰과 암호화 키를 자동 전달한다. 시스템 Bluetooth 설정의 OS 페어링 QR이 아니라 두 Relay 앱을 연결하는 QR이다.

연결 완료 후 Galaxy **테스트 이벤트 보내기**로 확인한다. 앱이 화면에 보일 때는 알림함을 갱신하고, iOS가 background BLE 수신을 허용한 때에는 로컬 알림도 표시한다. QR 재생성은 이전 native 연결을 폐기하므로 평소 재연결 때마다 누르지 않는다. 네이티브 모드의 새 알림은 PWA에 보내지 않으며 이전 PWA 기록은 자동 이전하지 않는다.

## 조작

| 조작 | 동작 |
|---|---|
| 묶음 탭 | 앱·대화 제목별 전체 알림 펼치기 |
| 개별 탭 | 상세/읽음 표시. **기록 유지** |
| 상세 본문 재탭 | Galaxy 원본 열기. OS 제한 시 Galaxy 확인 알림 |
| 개별/묶음 왼쪽 밀기 | iPhone 기록 삭제 + 해당 Galaxy 알림 제거 요청 |
| 상세 답장 | 활성 원본이 RemoteInput을 제공할 때만 전송 |
| 상단 새로고침 | BLE가 끊겼으면 서버에서 대기 알림 가져오기 |
| 설정 → 기록 삭제 | 확인 후 iPhone 로컬 기록만 삭제 |
| 설정 → 연결 해제 | 서버 토큰 폐기, Galaxy에도 BLE 키 폐기 요청 |

Galaxy 원본 제거 이벤트가 도착하면 iPhone 관련 기록도 삭제한다. 원격 삭제는 원본 앱 서버의 읽음 처리와 다르다. 답장은 Android 12 이상/잠금 해제/유효한 원본 답장 동작이 필요하다. 성공은 원본 앱에 전달했다는 뜻이며 상대방 수신/읽음을 판정하지 않는다. 해제 때 Galaxy가 오프라인이면 **인터넷을 복구한 후** Galaxy **네이티브 연결 해제**에서도 인증을 폐기한다. Galaxy 수동 해제는 서버 관계 확인이 필요하므로 오프라인 완료를 지원하지 않는다. 이미 서버에서 해제된 관계도 로컬 키를 정리할 수 있다.

## 수신 한계

- 이미 연결된 BLE는 양쪽 인터넷 없이도 사용할 수 있다.
- iOS bluetooth-central background wake 기회에 수신/저장/로컬 알림을 처리한다. 강제 종료/재부팅/서명 만료/OS 자원 회수 뒤 수신은 보장하지 않는다. 먼저 잠금 해제하고 Relay를 실행한다.
- BLE가 끊겼을 때 서버 수신은 **iPhone 앱이 열린 동안**이다. 양쪽 인터넷과 Galaxy FCM이 필요하다. 이 무료 네이티브 전용 버전에 APNs는 없다.
- 한 번에 6개씩 가져온다. KV 전파 때문에 약 1분 이상 걸릴 수 있고 최대 90초 기다린다. 실패하면 Galaxy Queue를 완료 처리하지 않는다. 열린 동안 완료 후 60초 간격으로 재시도한다.
- Always 위치 권한만으로 상시 서버 수신이 가능해지지 않는다. 위치 기능을 가장한 유지 동작을 구현하지 않았다.

## 다시 빌드

[비공개 저장소](https://github.com/simzaekong/galaxy-relay) → Actions → Build native iPhone IPA → Run workflow. 성공한 GalaxyRelay-unsigned-IPA artifact에는 IPA/checksum/시뮬레이터 스크린샷이 있다. 보관은 3일이므로 내려받아 보관한다. Apple 서명 없는 IPA는 AltStore 서명 후 설치한다.

GitHub macOS runner의 비공개 저장소 무료 사용량/비용 계산을 확인하고 [예산](https://docs.github.com/en/billing/concepts/product-billing/github-actions)을 설정한다. 매주 자동 빌드를 만들지 않았다. 별도 유료 서비스는 추가하지 않는다.

## 문제 해결

QR 생성 실패: Galaxy 등록/인터넷/근처 기기 권한/Bluetooth 광고 지원 확인.
QR 연결 실패: 새 QR, 양쪽 Bluetooth 권한, Galaxy 연결 서비스 확인하고 앱 재실행.
배너만 없음: iPhone 알림 권한/집중 모드 확인. foreground는 배너 없이 알림함을 갱신한다.
멀리 떨어져 수신 안 됨: iPhone Relay를 열고 새로고침. 서버 background 수신은 지원하지 않는다.
앱 실행 안 됨: AltStore 만료/개발자 모드/개발자 신뢰 확인.
답장 실패: Galaxy 잠금 해제/최신 원본/원본 앱 답장 동작 확인.
원본 열기 확인 알림: Android background activity 제한의 정상 fallback이다.

[실행한 테스트와 남은 실제 기기 확인](native-tests.md).

## iPhone 2.1.0 디자인 업데이트

Android 2.0.8 APK와 iPhone 2.1.0 IPA를 기존 앱 위에 업데이트한다. 앱을 삭제하거나 기존 연결을 해제할 필요는 없다. iPhone IPA는 기존과 같이 AltStore에서 서명/설치한다. 기존에 연결된 기기는 저장된 정보로 재연결한다.

홈 탭에서 연결 진행/상태와 최근 알림, 알림 기록 탭에서 앱 필터/검색/묶음 알림, 설정 탭에서 권한/개인정보/연결 해제를 이용한다. 묶음을 누르면 펼쳐지고 개별 알림을 누르면 상세를 연다. 상세 본문을 한 번 더 누르면 Galaxy 열기 요청이다. 왼쪽 밀기 삭제는 Galaxy에도 전달한다. 상세를 봤다는 이유만으로 기록을 지우지는 않는다.

실제 앱 아이콘은 Android 업데이트 이후 새로 발생하는 알림부터 표시한다. 이전 기록에는 기본 아이콘이 보일 수 있다. 잠금화면 알림의 발신 앱 아이콘은 Galaxy Relay로 유지한다. 실제 Liquid Glass 탭/툴바/주요 버튼은 iOS 26 이상에서 표시되며 이전 iOS는 시스템 기본 디자인으로 동작한다. 라이트/다크 모드는 iPhone의 시스템 설정을 따른다. [디자인과 호환성](native-design.md).

## Android 2.0.9 / iPhone 2.1.1 자동 재연결 업데이트

양쪽 앱을 기존 앱 위로 업데이트하고 각각 한 번 연다. 기존 연결 정보와 기록은 유지하므로 연결 해제나 새 QR을 먼저 만들지 않는다. iPhone IPA는 AltStore로 서명/업데이트한다. Galaxy 설정 → 앱 → Galaxy Relay → 배터리에서 제한 없음을 선택하고 앱을 강제 중지하지 않는다. iPhone도 앱 전환기에서 Relay를 밀어 종료하지 않는다.

Bluetooth OFF/ON, 일시적으로 범위를 벗어남, 짧은 전송 오류 후 기존 연결로 복구를 시도한다. 문제가 다시 생기면 Galaxy 고급 → 블루투스 연결 진단의 연결 이력과 iPhone 상태를 확인한다. 이력에는 알림 내용·키·토큰·기기 주소를 넣지 않는다. [분석, 변경, 장시간 실기기 확인 절차](ble-recovery.md).

## Android 2.0.10 / iPhone 2.1.2 배터리 표시

양쪽을 기존 앱 위로 업데이트하고 한 번씩 실행한다. 추가 권한이나 새 QR은 필요 없다. 연결 영역에서 Galaxy/iPhone 각각의 잔량·충전 상태와 상대의 마지막 수신 시각을 확인한다. 기기별 잔량을 아직 받지 못하면 대시 또는 수신 전 안내가 표시된다.

연결 직후 교환하고 이후 잔량 변화는 최소 5분, 충전 상태 변화는 최소 1분 간격으로 합친다. 변화가 없으면 보내지 않는다. 연결이 끊기면 마지막 값만 표시하며 실시간 조회를 위해 서버를 계속 호출하지 않는다. 절전 때문에 OS 이벤트가 늦으면 실제 반영도 늦을 수 있다. [배터리 전력 정책과 위치 질문](battery-sync.md).


## 2.0.1 연결 해제 복구

서버 응답과 관계없이 iPhone에서 연결 해제를 누르면 BLE 검색/예약 재연결/세션/저장된 pair를 정리한다. 서버 인증 폐기에 실패하면 폐기 전용 대기 정보만 Keychain에 보관하고 앱 실행/활성화 때 재시도한다. 이 정보로 BLE 수신이나 명령을 다시 활성화하지 않는다. Galaxy가 오프라인이면 서버 및 Galaxy 측 키 폐기는 즉시 완료되지 않을 수 있다.

BLE가 연결되지 않았을 때는 저장된 연결 정보가 있어도 **Galaxy QR로 연결**이 표시된다. 기존에 검색 상태에서 막혔다면 새 IPA를 기존 앱에 덮어 설치하고 설정에서 연결 해제한 뒤 Galaxy에서 새 QR을 만들어 스캔한다. 기록 삭제나 앱 제거는 필요 없다. 연결 해제 직전에 시작한 서버 동기화/명령의 늦은 결과도 새 연결 상태를 덮어쓰지 않는다.

## 선택형 전화 수신 화면

Galaxy 설정 → 전화 및 직접 SMS 수신에서 전화 상태 권한을 허용하고 전화 상태 전달을 켠다. iPhone 설정 → Galaxy 전화 알림 → 전화 수신 화면 및 벨소리를 켜고 안내를 확인한다. BLE 연결 완료 후 실제 Galaxy 수신 이벤트에만 CallKit 화면을 요청한다. iPhone 받기는 Galaxy 전화를 받지 않는다. iPhone의 거절·종료는 live BLE로 실제 Galaxy 통화를 끊는다. Android에서 iPhone 전화 거절·끊기 권한을 추가로 허용해야 한다. Galaxy에서 받으면 iPhone 통화 중 화면으로 전환한다. 음성 전달은 없다. 무료 계정 재서명·실기기 벨소리/잠금 화면은 별도 인수 시험이 필요하다. [동작과 제한](call-alerts.md).

2.2.0에서 통화형 알림이 실패했다면 2.2.1 이상 IPA로 덮어 설치한다. CallKit용 voip 선언을 보완했다. 계속 일반 알림만 오면 iPhone 설정 → Galaxy 전화 알림 → 마지막 CallKit 요청 결과의 CK 코드를 확인하거나 복사한다. 설정과 기존 페어링은 유지하며 새 QR은 필요하지 않다.
