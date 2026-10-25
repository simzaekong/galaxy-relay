# Galaxy 전화 상태와 원격 종료 · iPhone 2.3.0 / Android 2.2.0

## 사용

양쪽 앱을 덮어 설치한다. QR을 다시 연결할 필요는 없다. Galaxy Relay 설정 → 전화 및 직접 SMS 수신 → iPhone에서 전화 거절·끊기 권한을 허용하고 전화 상태 전달을 켠다. iPhone 설정 → Galaxy 전화 알림 → 전화 수신 화면 및 벨소리를 켠다.

- Galaxy 전화 수신: 기존 CallKit 수신 화면과 벨소리.
- iPhone 받기: Galaxy 전화를 받지 않는다. iOS action은 실패로 완료하고 Galaxy에서 받으라는 안내를 표시한다. 시스템 받기 버튼의 문구나 존재는 앱에서 바꿀 수 없다.
- Galaxy에서 받기: 해당 수신의 최신 live BLE offhook 이벤트로 iPhone CallKit에 연결 상태 전환을 요청한다. 앱 내 통화 화면에도 시간과 Galaxy 통화 종료 버튼을 제공한다. iOS가 시스템 연결 상태 전환을 거부하면 수신 화면을 종료해 벨소리를 멈추고, 앱을 열면 앱 내 통화 화면을 사용한다. 통화 음성은 Galaxy/연결된 이어폰에서만 처리한다. iOS 화면 전환은 실제 기기 확인 대상이다.
- iPhone 거절·종료: 현재 통화 ID로 서명된 CALL_HANGUP을 live BLE로 한 번 보낸다. Galaxy의 실제 ended 이벤트를 받아야 성공 처리한다. 응답이 없으면 4초 후 실패 표시하고 자동 재전송하지 않는다. 실패 상태에서는 Galaxy에서 직접 확인한다.
- Galaxy/상대방에서 종료: iPhone 상태와 CallKit 화면도 종료한다. BLE 단절·기능 끄기는 iPhone 표시만 닫으며 Galaxy 통화를 끊지 않는다.

시간은 Galaxy의 OFFHOOK 전환 시각부터 iPhone에서 계산하며 초마다 BLE 패킷을 보내지 않는다. 기본 PHONE_STATE 기반이므로 교환망의 정확한 연결 시각과 오차가 있을 수 있다. 발신 통화는 추적하지 않는다.

## 제어 제한과 선택 이유

Android 공개 TelecomManager.endCall()은 API 28부터 존재하지만 API 29부터 deprecated다. 개인 설치에서 ANSWER_PHONE_CALLS 사용자 권한을 명시적으로 받아 사용하며, One UI/OS가 거부하면 실패를 반환한다. 비공개 API·접근성 자동 터치·루팅·기본 전화 앱 강제 교체를 사용하지 않는다. [Android 공식 API](https://developer.android.com/reference/android/telecom/TelecomManager#endCall()).

endCall API는 특정 Telecom Call 객체를 지정하는 방식이 아니라 현재 통화에 적용되므로, broadcast 추적과 종료 사이의 극히 짧은 상태 변경까지 원자적으로 보장하지는 못한다. 단일 통화 개인용으로 제한한다. 종료 대상 오인 가능성을 줄이기 위해 active SIM이 정확히 1개이고 IDLE에서 시작한 단일 수신일 때만 허용한다. 듀얼 SIM 활성, 통화 대기/병합, 권한 없음, ID 불일치, 5초 지난 명령, Bluetooth 단절, Android 8.x에서는 종료하지 않는다. 통화 대기 발생 시 기존 iPhone 표시를 닫고 제어를 중단한다. 지원되지 않는 상태에서도 일반 통화 감지는 가능하다.

HMAC, UUID command/nonce, 기존 DB dedup, 현재 callId 재검증을 적용한다. 종료 명령은 서버/FCM/오프라인 큐로 보내지 않는다. 오래된 명령이 나중에 다른 통화를 끊지 않게 하기 위함이다. 통화 상태 이벤트는 기존 암호화 경로를 사용한다. 상태만 전달하며 마이크/오디오 캡처·PushKit·APNs는 추가하지 않는다.

## 실제 기기 시험

다른 휴대폰으로 Galaxy에 전화 → iPhone 수신 화면 → Galaxy에서 받기 → iPhone 통화 시간 표시 → iPhone 종료 → Galaxy 실제 종료. 수신 중 iPhone 거절도 별도로 시험한다. Galaxy에서 직접 종료, 상대방 종료, 권한 거부, 명령 지연, Bluetooth 단절, 통화 대기/듀얼 SIM 차단도 확인한다. 자동 테스트만으로 실제 Telecom/CallKit 서비스 성공을 보장하지 않는다.

## 이전 버전 진단 기록

# Galaxy 전화 수신 화면 · iPhone 2.2.1 (14)

## 사용

기존 iPhone 앱 위에 새 IPA를 AltStore로 설치한다. Galaxy는 2.1.0을 그대로 사용한다.

1. Galaxy 설정 → 전화 및 직접 SMS 수신 → 전화 상태 권한을 허용하고 전화 상태 전달을 켠다. 발신자 이름/번호는 별도 발신자 번호·연락처 권한과 OS 제공 여부에 따른다.
2. iPhone 설정 탭 → **Galaxy 전화 알림 → 전화 수신 화면 및 벨소리**를 켠다. 처음에는 꺼져 있으며 기능 설명을 확인해야 켜진다.
3. 양쪽 BLE 연결 완료 후 Galaxy로 실제 전화를 걸어 시험한다. iPhone에 CallKit 수신 화면과 시스템 벨소리를 요청한다. 실제 표시와 소리·진동은 iOS의 집중 모드·무음·기기 상태에 따른다.
4. Galaxy에서 받기/offhook, 거절/종료/ended가 오면 iPhone 수신 화면을 닫는다.

## 버튼의 의미

**실제 통화는 iPhone으로 연결되지 않는다.** 시스템의 받기·거절 버튼 이름은 변경할 수 없다. 두 버튼 모두 이 iPhone의 수신 알림만 닫는다. Galaxy 전화 수락·거절 명령은 보내지 않는다. 통화는 Galaxy에서 직접 받거나 거절해야 한다. 수신자 이름에도 ‘Galaxy 알림’을 붙이며, 설정에서 이 동작을 명시한다.

CallKit는 잠금 상태와 iOS 설정에 따라 배너/전체 화면을 선택한다. 앱이 항상 전체 화면을 강제하지 않는다. iPhone에 이미 다른 통화가 감지되면 새로운 CallKit 알림 대신 일반 알림을 요청해 기존 통화를 방해하지 않도록 한다.

## 처리와 제한

- 인증된 BLE로 받은 실제 type=call, state=ringing 이벤트에만 사용한다. 인터넷 폴링으로 뒤늦게 받은 수신 기록은 CallKit로 울리지 않는다. 실제 수신 없이 벨을 시험하는 production endpoint나 자동 가짜 통화를 만들지 않는다.
- 최초 이벤트가 15초보다 오래되거나 5초 넘게 미래인 경우 무시한다. 같은 callId의 중복 수신은 새 UI를 만들지 않고, 이름 보강만 갱신한다. 종료된 통화/먼저 도착한 종료/확인한 통화는 다시 울리지 않는다.
- 통화는 한 번에 하나만 표시한다. 최초 수신 시각부터 최대 45초 타이머를 두고 이름 갱신으로 연장하지 않는다. BLE 연결 단절, 수동 연결 해제, 기능 끄기, iOS background task 실행 시간 만료, provider reset에도 종료한다.
- CallKit 보고 중에 종료 이벤트가 오거나 기능이 꺼져도, 뒤늦게 성공한 보고를 다시 종료해 화면이 남지 않도록 한다.
- iOS가 CallKit 보고를 거부하면 아직 유효한 현재 수신에 한해 일반 알림으로 fallback한다. 같은 시도의 실패를 재전송하거나 계속 재시도하지 않는다. 미리보기 숨기기를 CallKit 발신자 이름에도 적용하고 변경 시 활성 이름도 숨긴다.
- 앱 종료·OS 강제 종료·Bluetooth 단절 중에는 수신 및 종료 전파를 보장하지 않는다. 서버만으로 잠든 앱을 깨우는 기능은 추가하지 않는다. iOS가 앱 실행을 중단할 때 종료 처리의 실제 시점은 실기기에서 검증해야 한다.

## 서명과 오디오

CallKit 프레임워크와 기존 Bluetooth 수신만 사용한다. CallKit 요청에 필요한 UIBackgroundModes의 voip 선언을 포함한다. APNs/PushKit/Communication Notifications entitlement, audio background mode, 마이크 권한, AVAudioSession, 실제 음성 중계는 추가하지 않는다. voip 선언을 추가해도 PushKit 등록·서버 푸시·상시 네트워크 연결을 시작하지 않는다. unsigned IPA 빌드 성공은 무료 Apple 계정의 재서명·실기기 벨소리 동작을 입증하지 않는다. AltStore 설치 및 잠금 상태의 실기기 인수 시험이 필요하며, 동작하지 않으면 기능을 끄고 일반 알림을 사용한다.

## 인수 시험

실제 Galaxy/iPhone에서 (1) 잠금/해제 각각 수신 화면·벨소리, (2) Galaxy 받기·거절·발신자 취소 시 닫힘, (3) iPhone 받기·거절은 알림만 닫고 Galaxy는 그대로 수신 중, (4) BLE 단절/기능 끄기 시 닫힘, (5) 종료 신호 없는 경우 타임아웃, (6) 반복/지연 이벤트로 재울림 없음, (7) 실제 다른 iPhone 통화 중 방해 없음, (8) 앱 강제 종료/집중 모드/무음에서의 제한을 확인한다. 실제 통화 테스트에서는 iPhone에서 음성이 전달된다고 오해하지 않도록 Galaxy에서 받는다.

공식 API: [CallKit](https://developer.apple.com/documentation/callkit), [수신 보고](https://developer.apple.com/documentation/callkit/cxprovider/reportnewincomingcall(with:update:completion:)), [Provider 설정](https://developer.apple.com/documentation/callkit/cxproviderconfiguration).

## 2.2.1 수정 및 진단

2.2.0에는 CallKit용 voip 선언이 빠져 있었다. Apple은 CallKit unentitled 오류의 대표 원인으로 UIBackgroundModes의 voip 누락을 안내한다. 실제 기기에서 받은 이전 오류 코드를 저장하지 않았으므로 해당 실패의 유일한 원인이라고 확정하지는 않는다. 2.2.1은 선언을 보완하고 실제 생성된 앱 번들에서 voip가 있는지 회귀 테스트한다. [Apple 공식 설명](https://developer.apple.com/documentation/callkit/cxerrorcoderequesttransactionerror-swift.struct/code/unentitled).

설정 → Galaxy 전화 알림에서 마지막 CallKit 요청 결과와 시각을 확인하고 ‘진단 코드 복사’로 공유할 수 있다. 최근 한 건의 고정 코드·시각만 로컬 보관하며 발신자·번호·callId·원문 오류·서버 로그는 포함하지 않는다. 일반 통화 종료/앱 재시작으로 실패 원인이 지워지지 않으며 사용자가 진단 기록을 삭제할 수 있다.

| 코드 | 의미 |
|---|---|
| CK00 | iOS가 수신 화면 요청을 수락함 (실제 벨소리 재생 확인과는 별개) |
| CK01 | CallKit 설정/서명 권한 거부 |
| CK02 | 집중 모드로 차단 |
| CK03 | iOS 차단 설정으로 차단 |
| CK04 | 같은 수신 화면 중복 등록 |
| CK05 | 다른 iPhone 통화 감지 |
| CK06 | 백그라운드 실행 시간 미허용 |
| CK99 | 분류되지 않은 iOS 거부 |

실패하면 여전히 일반 알림으로 fallback한다. 무료 계정의 새 서명 및 실제 수신 화면·벨소리·잠금 상태는 새 IPA로 실기기에서 다시 검증해야 한다.

테스트는 연결된 iPhone과 별개의 다른 번호에서 Galaxy로 걸어야 한다. 연결된 iPhone 자체에서 Galaxy로 전화하면 그 iPhone은 이미 실제 통화 중이므로 보호 분기에 의해 일반 알림으로 처리하고 CK05를 남긴다. 기존 iPhone 통화를 끊고 가짜 수신 화면으로 대체하지 않는다.
