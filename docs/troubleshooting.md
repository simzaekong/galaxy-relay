# 문제 해결과 OS 제한

| 증상 | 점검·대응 |
|---|---|
| Backend 등록 실패 | HTTPS origin만 입력. path/query/credentials 없이 주소 설정. INSTALL_SECRET 로컬 확인. google-services.json package·Firebase project 일치 확인 |
| Pairing 코드가 거절됨 | 5분 만료/이미 claim/분당 rate limit 여부 확인. Galaxy에서 새 코드 생성. 이전 승인 요청을 승인하지 않기 |
| PWA가 승인 대기 | Galaxy 연결 상태 확인 → 양쪽 8자리 승인 코드 비교 → 승인 → PWA 새로고침 |
| Push 허용 버튼 동작 안 함 | iOS 16.4+ Safari 공유 → 홈 화면에 추가한 **홈 화면 앱**에서 실행. 알림 활성화 버튼 직접 누르기. OS 설정에서 Relay 알림 권한 확인 |
| 권한 허용 후 알림 없음 | Pairing 완료 및 구독 Backend 등록 여부, Galaxy 인터넷/알림 접근/앱 필터, Worker secret VAPID 설정 확인. Galaxy 테스트 이벤트로 앱별 문제 분리 |
| Push subscription 만료 | PWA 설정 알림 활성화로 재등록. origin/VAPID 키를 바꾼 경우 기존 구독 unsubscribe·사이트 재연결 필요 |
| E2EE 알림을 읽지 못함 | 양쪽 key 지문 일치 확인. 키를 서버나 채팅으로 보내지 않기. PWA 저장 공간이 지워졌으면 직접 키 재설치. Android OFF + PWA 키 유지 상태는 평문 거부 |
| Galaxy 일반 알림이 안 옴 | Notification Access 허용, 전달 설정/선택 앱 ON, 알림에 title/text 존재 여부, 지속/그룹 summary 필터 확인 |
| 카카오톡 단체방·앱 알림 일부 누락 | 원본 앱의 잠금화면 미리보기·보안 설정·묶음 알림이 내용/Intent를 제한할 수 있음. 알림 접근이 원본 앱 내부 데이터 접근은 아님 |
| 폰에서 열기가 즉시 실행되지 않음 | 정상 fallback 가능. Galaxy의 iPhone에서 열기 요청 알림을 탭. Relay 알림 표시 권한 필요. 잠금 해제. 원본 알림이 아직 활성인지 확인 |
| 알림을 지웠는데 원본 메시지는 미읽음 | 정상. Android Notification 제거는 KakaoTalk/Discord/Instagram 서버 읽음 처리와 별개 |
| 요청 수락 후 결과 없음 | FCM high priority도 보장 전달은 아님. Galaxy 강제 종료/Doze/절전/인터넷/FCM token, iPhone Push 권한 확인. 2분이 지난 명령은 실행하지 않음 |
| 오래된 기록을 다시 받을 수 없음 | 서버 알림 DB가 없으므로 복구/백필 없음. Queue·Push TTL 초과나 브라우저 데이터 삭제 후 원문 복원 불가 |
| SMS 직접 수신 권한을 못 줌 | Android/설치 경로의 restricted SMS permission 정책. 일반 메시지 앱 Notification 전달 사용. 기본 SMS 앱 역할/READ_SMS를 강요하지 않음 |
| 전화번호·연락처가 없음 | APK 1.1.0 → 발신자 번호·연락처 권한(선택) 허용, 전화 상태 전달 ON. READ_CALL_LOG 제한/번호 비공개일 때 전화 앱 알림 정보로 보강하며 항상 번호·이름을 얻을 수는 없음 |
| 삭제·요청 결과 배너가 계속 옴 | Android도 1.1.0으로 업데이트. 이전 APK의 암호화 control Push는 Worker에서 구분할 수 없음. PWA 재실행으로 최신 SW 적용 |
| 전화가 끝났는데 iPhone 수신 배너가 남음 | 홈 화면 PWA를 열어 상태 조회. 화면 visible일 때 5초 간격 + KV 지연. 완전히 닫힌 PWA의 즉시 회수는 iOS silent Push 제한으로 보장되지 않음. 24시간 후 상태 TTL이 지나면 오래된 배너는 직접 제거 |
| 기록·검색을 오프라인에서 못 읽음 | 최초 온라인 실행·SW 설치 완료 필요. 앱 저장 공간 삭제/OS 축출은 오프라인 복원 불가. 명령 버튼 비활성화는 정상 |
| 새 버전이 보이지 않음 | 인터넷 연결 후 앱 재실행. Service Worker 업데이트가 되면 새 캐시로 전환/열린 화면 갱신. 최후 수단 사이트 데이터 삭제 전 기록 복구 불가 확인 |
| Rate limit / 503 반복 | 잠시 기다린 뒤 재시도. 무료 Worker/KV CPU/request/read/write 한도 확인. Worker의 본문 로그를 켜지 않고 endpoint/에러 코드만 확인 |
| APK 설치/빌드 실패 | min Android 8(API26), compile SDK36, JDK17+, Gradle Wrapper 사용. 기존 다른 서명 APK는 업데이트 불가하므로 기록/연결을 정리한 뒤 설치 |

## Galaxy / One UI

설정 → 앱 → Galaxy Relay → 배터리에서 사용을 허용하고, 백그라운드 사용 제한/절전 앱 목록에서 Relay를 제외한다. 제조사 버전에 따라 경로 이름이 다르다. 불필요한 무제한 배터리 권한/Foreground Service를 자동 요청하지 않는다. Android 앱 강제 종료는 서비스·FCM을 멈출 수 있어 다시 앱을 실행해야 한다. 재부팅·절전 모드·잠금 중 실제 시험이 필요하다.

원본 `contentIntent`의 Activity 실행 제한은 [Android 공식 BAL 정책](https://developer.android.com/guide/components/activities/background-starts)을 따른다. Relay 화면이 실제 표시될 때만 직접 실행하며 백그라운드에서는 확인 알림을 사용한다. `PendingIntent.send()`가 반환되어도 대상 앱의 특정 채팅방 도착을 검증할 수 없다. 원본 앱이 Intent를 취소/변경하거나 이미 알림이 없어지면 열 수 없다. Main Activity로 자동 대체하지 않는다. 알림을 탭해도 원본 앱의 로그인/보안 화면이 먼저 나올 수 있다.

전화는 Notification 방식의 Web Push다. 통화 Audio Relay/CallKit UI/잠금 우회는 없다. 번호와 연락처를 항상 보여주는 것처럼 표시하지 않는다. SMS 직접 수신에는 원본 Notification 매핑이 없어 열기/삭제 버튼을 제공하지 않는다. 기본 메시지 앱 알림 방식에는 원본 Intent가 있을 때 동작한다.

## iPhone / Safari

[WebKit iOS 16.4 Web Push 안내](https://webkit.org/blog/13966/webkit-features-in-safari-16-4/)와 [Apple Web Push 문서](https://developer.apple.com/documentation/usernotifications/sending-web-push-notifications-in-web-apps-and-browsers)를 따른다. 홈 화면 앱/사용자 클릭 권한 요청/userVisibleOnly가 필요하다. Focus·알림 요약·네트워크·OS 저장 공간/구독 정책이 지연·보관에 영향을 준다. “몇 초”를 목표로 하지만 지연 상한을 보장하지 않는다.

1.1.0에서는 상태 변경/명령 결과를 Push로 보내지 않고 열려 있는 PWA가 조회한다. 통화 상태를 받으면 해당 수신 알림만 닫는다. 완전히 닫힌 iPhone PWA를 새 배너 없이 깨울 수는 없다. Push banner 표시만으로 read/ACK하지 않는다. 실제 화면이 visible인 상태에서 해당 상세를 열었을 때만 read 처리한다. 오프라인 상세를 열면 로컬 read만 처리하며 나중에 자동 삭제 명령을 뒤늦게 전송하지 않는다.

## 개발 중 검증

Backend `npm run check && npm test`, Android `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, web `npm test`를 먼저 확인한다. PWA 테스트는 로컬 Worker 서버가 필요하다. 실제 integration에는 별도 test-fixture APK를 테스트 emulator에 설치해야 한다. 테스트가 실패할 때 service account key/token/본문을 출력하지 말고 HTTP status 및 비민감 오류 코드만 점검한다.

## 답장 입력창이 없거나 보내지지 않음

- APK 1.2.0 업데이트 후 도착한 새 알림인지 확인한다. Android 12 이상이어야 한다. 원본 알림에 자유 입력 답장이 없으면 PWA에서도 지원하지 않는다.
- Galaxy를 잠금 해제하고 알림 접근 권한을 유지한다. 갱신되거나 사라진 기록에서는 최신 알림으로 이동한다.
- 직접 SMS 수신을 켰다면 문자 앱 알림 방식으로 전환한다. SMS 이벤트만으로는 원본 앱 답장 Intent가 없다.
- E2EE 기존 수신 키는 동일한 Galaxy 키를 PWA 설정에서 다시 저장한다. 양쪽 키 지문을 비교한다.
- 결과가 불확실하면 원본 앱에서 실제 전송 여부부터 확인한다. 중복 방지 대기는 최대 5분이며 답장을 자동 재전송하지 않는다.
- ‘원본 앱에 답장을 전달’ 상태는 상대방 전달 완료를 뜻하지 않는다. [자세한 제한](reply.md).

## iPhone 전화 수신 화면이 나오지 않음 (2.2.0)

Galaxy에서 전화 상태 권한과 전화 상태 전달을 켰는지 확인한다. iPhone은 설정 → Galaxy 전화 알림 → 전화 수신 화면 및 벨소리를 켜야 한다. 정상 BLE 연결 중 실제 수신 이벤트에만 적용하며, 서버로 뒤늦게 동기화한 과거 수신은 울리지 않는다. 15초 이상 지연된 수신이나 종료된 통화도 다시 울리지 않는다. iOS가 CallKit를 거부하거나 이미 다른 iPhone 통화가 감지되면 일반 알림으로 돌아간다. 집중 모드·무음·OS 실행 제한을 확인한다. CallKit가 표시되어도 iPhone에서 통화할 수 없으며 받기/거절은 해당 알림만 닫는다.

Galaxy에서 전화가 종료되어도 iPhone 화면이 남는다면 BLE 연결·종료 이벤트 전달을 확인한다. 수신 알림은 BLE 단절/기능 끄기/최대 45초 타이머/iOS 실행 시간 만료에도 닫히게 설계했다. 앱 강제 종료 등에서의 실제 처리 시간은 실기기 검증이 필요하다. 기능을 끄면 일반 전화 알림 방식으로 돌아간다. App Store 심사나 무료 계정 설치 성공을 자동 빌드 결과만으로 보장하지 않는다.

### 켜져 있는데 ‘통화형 알림을 표시하지 못했습니다’라고 표시됨

iPhone 2.2.0은 CallKit용 UIBackgroundModes의 voip 선언이 빠졌고 실제 거부 원인을 bool로만 처리했다. 2.2.1은 이를 보완하고 마지막 요청 결과를 앱 재시작/통화 종료 후에도 표시한다. 기존 오류의 정확한 native code는 남지 않았으므로 설정 누락만으로 모든 실패를 확정하지 않는다.

최신 IPA로 덮어 설치하고 BLE 연결 후 새 실제 수신으로 시험한다. 설정 → Galaxy 전화 알림 → 마지막 CallKit 요청 결과 → 진단 코드 복사. CK01 설정/서명 거부, CK02 집중 모드, CK03 차단 설정, CK04 중복, CK05 다른 통화 감지, CK06 실행 시간 미허용, CK99 기타. CK00은 보고 수락이며 벨소리 재생 확인은 아니다. [상세 코드](call-alerts.md).


## 원격 거절·끊기가 작동하지 않음 (iPhone 2.3.0)

양쪽 앱을 iPhone 2.3.0 / Android 2.2.0으로 업데이트하고 Galaxy Relay 설정 → 전화 및 직접 SMS 수신 → iPhone에서 전화 거절·끊기 권한을 허용한다. BLE 연결된 현재 수신 통화만 대상이다. 활성 SIM이 2개이거나 통화 대기/병합 상태이면 잘못된 통화를 끊지 않도록 차단한다. 최신 Android의 공개 endCall API가 기기에서 거부되면 Galaxy에서 직접 끊어야 한다. iPhone 받기는 지원하지 않으며 Galaxy에서 받아야 통화 중 상태로 전환한다.


## 배터리 수신 시각이 오래됐는데 연결됨으로 표시되고 전화가 안 옴

기존 버전은 배터리 값이 변하지 않으면 갱신하지 않았고, 인증 후 무응답 peer를 주기적으로 확인하지 않았다. Android 2.2.1 / iPhone 2.3.1로 양쪽을 업데이트하고 한 번씩 열어 재연결한다. 마지막 통신 확인과 배터리 갱신 시각은 별개다. Galaxy 전화 상태 전달과 권한을 확인하고 고급 → 전화 수신 진단에서 실제 수신이 감지됐는지 확인한다. 강제 종료/One UI 강제 절전/알림 권한과 집중 모드도 별도 원인일 수 있다. [상세 동작](connection-health.md).
