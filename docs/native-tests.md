# 네이티브 2.0 검증 · 2026-10-14

| 검사 | 결과 |
|---|---|
| Backend TypeScript | tsc 성공 |
| Backend Vitest | 3 files **21 tests PASS** |
| Android APK | Debug/Release/AndroidTest 빌드 성공 |
| Android lint | Debug/Release 성공 |
| Android 단위 | **7 tests PASS** |
| macOS Swift protocol | **4 tests PASS** |
| iOS iphoneos | unsigned arm64 IPA 생성 성공 |
| iOS simulator | 빌드·설치·실행·스크린샷 성공 |
| Android15 emulator | 설치/메인 실행 성공 |
| 실제 Worker/FCM | 최종 합성 fixture 수신·답장·중복/결과 반환·native revoke 성공 |

Android 암호화 tests는 P256/HKDF/GCM/AAD/프레임/독립 Node 벡터, Swift tests는 protocol/QR 만료/프레임/같은 독립 벡터를 검증한다. Backend는 토큰/role/nonce/timestamp/pair 만료·재사용/rate limit/FCM·Push 실패/native grant scope·expiry/ciphertext 강제/revoke를 검증한다. production test endpoint를 만들지 않았다.

실제 cloud 시험: 별도 emulator Android 등록→native credential→NotificationListener가 합성 fixture 수집→Room→실제 FCM pull→암호문 Worker packet→native-compatible 복호화→ACK. 암호화 답장을 실제 FCM/원본 RemoteInput receiver까지 전달하고 동일 command가 두 번 실행되지 않는 것과 암호화 결과 반환을 확인했다. 실제 연락처로 보내지 않았다. 시험 device/token은 종료 시 revoke했다.

이 cloud 시험의 iPhone 역할은 독립 Node client다. 실제 iPhone 무선 수신 검증은 아니다. iPhone simulator는 앱 실행을 확인하며 Bluetooth 무선 기능을 검증하지 않는다. 이전 PWA 실제 기기 결과를 native BLE 검증으로 대체하지 않는다.

## 물리 기기에서 남은 인수 항목

1. AltStore 무료 계정 설치/7일 refresh/동일 앱 기록 유지.
2. Galaxy QR→iPhone scan→BLE 인증/서버 자동 설정.
3. 일반·카카오톡·문자·Instagram·Discord→native history/background local notification.
4. 전화 발신자/받기·거절·종료 시 ringing 배너 닫기.
5. 묶음/상세 기록 유지/왼쪽 삭제/Galaxy swipe 후 iPhone 기록 삭제.
6. 상세 재탭→원본 contentIntent 또는 Galaxy fallback/잠금 보호.
7. 실제 앱 RemoteInput 답장/실패·중복 처리.
8. 인터넷 없는 BLE, 거리 이탈 시 **열린 앱** cloud receive, BLE 재연결.
9. background/강제 종료/재부팅/서명 만료 뒤 제한과 복구.
10. 양쪽 해제/오프라인 해제 후 재연결 차단.

## 재현

Backend에서 npm ci, npm run check, npm test.
Android JBR/SDK 설정 후 android/gradlew.bat -p android :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease. 로컬 excluded signing 설정 없으면 unsigned release이다.
GitHub Build native iPhone IPA는 swift test와 iphoneos/simulator build/launch를 실행한다.

tools/test-native-cloud.mjs는 전용 emulator-5580/debug+계측+test-fixture APK/Git 제외 .secrets/cloudflare.json이 필요하다. 사용자 실제 기기용 시험이 아니다. 테스트가 끝나면 서버 pair를 폐기한다.

최종 재시험도 PASS. 알림 접근 재연결, emulator 부팅 후 DNS 준비, 계측 종료 전 SharedPreferences 저장 문제를 복구한 뒤 전체 경로를 재검증했다. 실사용 QR 생성/READY에서도 인증 저장을 확정한다. native revoke의 실제 FCM 전달로 Android 저장 BLE 키가 제거되는 것을 확인했다.


## iPhone 2.0.1 연결 해제 회귀 (2026-10-15)

- 서버 연결 실패 시 로컬 pair 제거, BLE 검색/재연결 중단, 새 QR 연결 가능 여부를 검증한다.
- 미완료 QR 세션 stop 후 connect가 검색을 재개하거나 이전 세션으로 명령을 보내지 않는지 검증한다.
- 이미 폐기된 인증(HTTP 401)은 연결을 되살리지 않고 폐기 대기열에서 정리하는지 검증한다.
- 실제 앱 코드를 XCTest host에서 실행한다. 최초 무서명 simulator 테스트는 Keychain 권한이 없어 실패했다. CI simulator test에만 임시 서명과 Keychain entitlement를 추가했다. 배포 IPA는 기존대로 unsigned이며 AltStore에서 사용자 계정으로 서명한다.
- 물리 iPhone의 Bluetooth 무선 동작, QR 카메라, AltStore 업데이트 보존은 별도 기기 확인 대상이다.

최종 결과: [GitHub Actions 38737732322](https://github.com/simzaekong/galaxy-relay) 성공. 프로토콜 테스트 4개와 실제 앱 회귀 테스트 3개 통과, iPhone Release/Simulator build 및 실행 성공. IPA 2.0.1 build 5의 SHA256과 Info.plist 버전을 로컬에서 검증했다.

## iPhone 2.1.0 / Android 2.0.8 (2026-10-15)

- 사용자가 Android 2.0.7의 G03-C 수정 후 실제 iPhone/Galaxy 연결 성공을 확인했다. 2.0.8은 해당 BLE 로직을 유지한다.
- Android Debug/서명 Release, JVM 19개, Debug/Release lint 오류 0개 통과. 기존 lint 경고 27개는 남아 있다.
- [CI 38752274407](https://github.com/simzaekong/galaxy-relay) 성공. Xcode 26.3/iOS 26.2 SDK의 iPhone/Simulator build, protocol 6개, hosted app 9개 통과.
- 실제 시뮬레이터의 홈 라이트/다크, 알림 묶음 목록, 알림 상세 PNG 4개를 내려받아 확인했다. 예시 알림/아이콘은 simulator 전용 fixture이며 실기기 알림을 촬영한 것이 아니다.
- artifact ZIP SHA256, 내부 IPA SHA256, IPA zip 무결성, iPhone 2.1.0 build 9 / bundle ID / 최소 iOS 16.0 확인. Android APK v2 서명 검증 통과.
- 아이콘 없는 기존 데이터, 정상 아이콘 decode/보존, 비정상/과도한 크기 이미지 거부, 다수 아이콘 기록 재실행 복원, 네트워크 크기 제한 유지, 상세 확인 후 기록 보존 및 Galaxy 제거 반영 회귀를 추가했다.
- 새 버전의 실기기 UI 및 Galaxy 원본 앱 아이콘 전송, AltStore 업데이트 후 상태 보존은 실기기 확인이 남아 있다. 시뮬레이터 성공을 물리 BLE 검증으로 간주하지 않는다.

## 연결 해제 위치 · Android 2.0.11 / iPhone 2.1.3 · 2026-10-16

- Android Debug/Release APK 빌드 성공. JVM 32 tests, failures/errors 0. Debug/Release lint 각각 오류 0, 기존 경고 29. release APK v2 서명 검증 성공.
- [GitHub macOS CI 38882332028](https://github.com/simzaekong/galaxy-relay) 성공. iOS 소스 commit e57fe38daa59cae08a1a846b7466fd84680d6984에서 Swift protocol 21 + hosted 앱 XCTest 17 = 38 tests, 실패 0. 실제 기기용 unsigned arm64 IPA 및 simulator 빌드·설치·실행·라이트/다크 화면 확인 성공.
- 신규 자동 검증: 인증 완료 전 조회 금지, 단절 한 번당 한 시도, 반복/시계 역행 시 5분 제한, 권한/기능 비활성 및 수동 stop 시 조회 금지, 좌표·시각·정확도 검증, pair별 보호 저장 기록 격리, 기록 삭제, 앱 실행 및 인증 전 단절에서 저장 없음.
- Android 최종본은 조회 직전/결과 저장 직전의 페어링·권한·설정 재확인도 포함하며 동일 32 tests와 lint를 재통과했다. iOS CI 뒤의 후속 commit은 해당 Android 가드 및 문서만 변경한다.
- artifact 12038088245의 ZIP SHA-256, 내장 IPA SHA-256, ZIP 무결성, bundle ID 및 2.1.3 build 12 확인. 소스 패키지 비밀값 제외 검사 통과.
- 실제 Galaxy/iPhone에서 GPS·화면 잠금 상태·단절 감지 지연·재연결과 GPS 취소·권한 변경·배터리 소모 계측은 아직 수행하지 않았다. 자동 검증은 이 물리 인수 시험을 대체하지 않는다. [설정·인수 절차](disconnect-location.md).

## Galaxy 2.1.0 UI · 2026-10-16

- Android Debug/Release 및 AndroidTest APK 빌드 성공. JVM 32 tests 실패 0, Debug/Release lint 오류 0/경고 28.
- API 35 읽기 전용 에뮬레이터에 설치·실행. DashboardTest 2 tests PASS: 실제 탭을 통한 홈/설정/알림 기록 이동, 홈과 설정의 위치 권한 진입 노출, 기록 없는 경우 지도 숨김, 진단 숨김, 기존 QR 교체 시 창 유지. 최종 기능본으로 재실행 통과 후 카드 제목 대비만 보완해 재빌드했다.
- 수동 UI 검사: 설정 탭 → 위치 권한 설정 → ‘연결이 끊긴 곳을 기억해요’ 안내 창 표시 확인. 동의 버튼을 누르기 전에는 위치 권한 창/측정이 시작되지 않는다. 새 UI 때문에 GPS를 켜거나 서버에 위치를 보내지 않는다.
- 시스템 라이트/다크 전환, 탭 복원, 홈·설정 스크린샷 확인. 앱 내부 아이콘은 벡터이므로 외부 이미지 호출 없음.
- 에뮬레이터 콜드 부트 초기에 System UI 응답 지연 창이 한 번 나타났으며 대기 후 복구했다. 이후 테스트와 화면 검사는 완료했다. 실제 Galaxy/iPhone의 위치·Bluetooth 무선 시험으로 간주하지 않는다.
- 설치 파일은 기존 release 키로 서명하며 기존 등록·페어링을 유지한다. iPhone은 이번 변경에 새 빌드가 필요하지 않으며 위치 카드가 있는 2.1.3을 사용한다. [화면/메뉴 안내](android-design.md).

## iPhone 2.2.0 CallKit 수신 알림 · 2026-10-16

- [macOS CI 38915021095](https://github.com/simzaekong/galaxy-relay) 성공. 검증한 iOS source commit bda0d474753a4d4ea46fda405dc26a23b287c8f2.
- Swift protocol **29 tests**, hosted 앱 XCTest **25 tests**, 총 **54 tests PASS**, 실패 0. 실제 기기용 unsigned arm64 IPA 및 simulator 빌드·설치·실행 성공. 설정 탭에 전화 수신 화면 및 벨소리 옵션과 안내가 표시되는 스크린샷 확인.
- 신규 순수 정책 8개: live/활성/최신 수신만 시작, 중복/이름 보강 시 타이머 연장 금지, 종료 먼저 수신 후 재울림 금지, 같은 통화만 종료, 다른 수신으로 활성 알림 교체 금지, 미래 시각에도 45초 상한, 단절/확인/끄기 이후 재울림 금지, 잘못된 ID/state 거부.
- 신규 mock provider 8개: 꺼짐/서버/과거 수신에서 system UI 생성 없음, 개인정보 이름 숨김과 보강, 보고 완료 전 종료와 늦은 성공 정리, 받기/거절의 로컬 확인 의미, 실패 fallback 1회, 단절 후 같은 통화 억제, 보고 중 비활성화, provider reset과 다음 통화.
- CallKit 진입점은 MainActor에서 생성하도록 컴파일 오류를 수정한 최종본이다. iOS가 background task 실행 시간을 주지 않으면 CallKit UI 대신 일반 알림을 사용한다. 정상 시도 후 provider/일반 알림의 늦은 완료가 종료된 통화를 복원하지 않도록 처리한다.
- artifact 12051713750 ZIP SHA-256 및 내장 IPA SHA-256/ZIP 무결성 확인. bundle ID 유지, 2.2.0 build 13, CallKit 링크 확인. PushKit·마이크 권한·추가 background mode 없음.
- **무료 Apple 계정 AltStore 재서명/설치, 실제 CallKit 수신 화면·시스템 벨소리·잠금 상태·집중 모드·실제 전화와의 공존은 아직 실기기에서 검증하지 않았다.** mock provider 통과를 실제 CallKit 서비스 성공으로 간주하지 않는다. [설치 및 물리 인수 절차](call-alerts.md).
- Android는 변경하지 않았다. 기존 2.1.0 APK의 빌드/32 JVM/2 UI 검사 결과를 유지하며, 이번 iPhone 기능 때문에 재빌드하지 않았다.


## iPhone 2.2.1 CallKit 설정·진단 수정 · 2026-10-17

- 사용자 시험은 연결된 iPhone이 아닌 다른 휴대폰에서 Galaxy로 건 전화였다. 따라서 연결된 iPhone의 기존 통화 보호 분기를 원인으로 보지 않는다. 이전 2.2.0은 네이티브 오류를 보관하지 않아 당시 정확한 거부 이유는 확정할 수 없다.
- 누락된 UIBackgroundModes의 voip 선언을 추가했다. 2.2.0의 mock provider 검사는 이 번들 설정 누락이나 실제 iOS 서비스 거부를 검출하지 못했다. 실제 앱 Info.plist 검사와 오류 분류·진단 저장/복원/삭제·성공 결과 갱신 회귀를 추가했다.
- [macOS CI 38922116934](https://github.com/simzaekong/galaxy-relay) 성공. iOS source commit 03f9af72a8c3d1f01edd3059b3103a14435b3527. Swift protocol 32 + hosted 앱 XCTest 28 = **60 tests PASS**, 실패 0. iPhone Release와 Simulator 빌드·설치·실행 성공.
- artifact 12056025747 ZIP SHA256 및 내부 IPA SHA256, ZIP 무결성을 로컬 검증했다. bundle ID 유지, 버전 2.2.1 build 14, background modes voip/bluetooth-central/location 확인. CallKit 링크 존재, PushKit 링크·마이크 권한·audio mode 없음. IPA SHA256: 302917bf42050ec208aff12bbc63de27ffbbc0888d01af71d287db2d695884d7.
- 진단은 코드·시각 한 건만 로컬 보관한다. 이름/번호/원시 오류를 저장하거나 서버에 보내지 않는다. 일반 알림으로 fallback해도 설정에서 CK 코드를 확인할 수 있다.
- **무료 계정 AltStore 재서명 이후 실제 iPhone의 CallKit 수신 화면·벨소리 성공은 아직 검증하지 않았다.** CI/mock 성공을 실기기 성공으로 간주하지 않는다. Android는 기존 2.1.0을 유지한다.


## 원격 통화 종료 · Android 2.2.0 / iPhone 2.3.0 · 2026-10-17

- 사용자가 이전 iPhone 2.2.1 수정 후 실제 전화 수신 표시가 정상이라고 확인했다. 이번 버전의 원격 종료/통화 중 전환은 아직 물리 기기에서 검증하지 않았다.
- Android 배포 app 모듈 Debug/Release 빌드 성공. JVM **35 tests**, 실패/오류 0. Debug/Release lint 오류 0, 경고 각 28. APK v2 서명 및 application ID, 2.2.0 versionCode 17 확인. 현재 추적한 단일 수신만 허용하고 다른 ID·발신/불명확 상태·5초 지난 시각·미래 시각·비정상 ID를 거부하는 정책 검사 포함.
- 최초 모듈 전체 lint 실행은 기존 test-fixture의 setSemanticAction API 28/minSdk 26 오류로 중단됐다. 해당 테스트 전용 앱은 이번 배포 대상이 아니며 변경하지 않았다. 최종 app 모듈 명시 빌드/테스트/lint는 위와 같이 통과했다.
- [macOS CI 38926114106](https://github.com/simzaekong/galaxy-relay) 성공, iOS source c4d40b95e6ffd1589bb659948cb31f55d69dbb92. Swift protocol **34** + hosted app **30** = **64 tests PASS**. iPhone Release/Simulator 빌드·설치·실행 및 설정 화면 확인. 최종 iOS 소스 이후 변경은 설치/보안/검증 문서뿐이다.
- 연결된 상태의 45초 수신 타이머 해제, 최신 live offhook만 연결 전환, 연결 전/후 종료, 지연된 show 완료의 복원 차단, iPhone Answer가 Galaxy 답신을 요청하지 않음, 원격 종료 실패 시 UI 유지, 중복 종료 요청 차단, 성공 확인 후 UI 종료를 검사했다. 실제 CallKit 전환/Telecom 호출은 mock 검사로 대체 확인했다고 주장하지 않는다.
- 첫 IPA 검증에서 plist의 고정 버전이 이전 값임을 발견해 배포하지 않았다. plist 버전을 MARKETING_VERSION/CURRENT_PROJECT_VERSION에서 가져오도록 수정하고 CI 전체를 재통과했다. iOS가 connected 전환을 거부하면 원래 수신을 종료해 계속 울리지 않게 하며, 다른 iPhone 통화가 있으면 자동 전환을 요청하지 않는다.
- 최종 artifact 12057162220 ZIP SHA256 및 내부 IPA SHA256/ZIP 무결성, bundle ID, iPhone 2.3.0 build 15, voip/bluetooth-central/location 확인. 마이크 권한/PushKit 없음. IPA SHA256 f5606d91487c8176a0b8bf73fc1295a3a53f76fbba092c2696e720498c2ef844.
- 물리 인수: AltStore 덮어 설치 → Android 종료 권한 허용 → 다른 휴대폰에서 수신 → Galaxy 받기 → iPhone 시간/상태 → iPhone 종료로 실제 Galaxy 종료. 수신 중 거절, Galaxy/상대 종료, 권한 거부, Bluetooth 단절, 다중 SIM/통화 대기 차단도 별도 확인 필요. endCall은 공개 deprecated API이므로 OS가 거부할 수 있고, 통화 객체를 직접 지정하는 원자적 제어는 아니다. [사용·제한](call-alerts.md).


## 장시간 대기 연결 확인 · Android 2.2.1 / iPhone 2.3.1 · 2026-10-17

- 사용자 보고: 배터리 마지막 수신 9시간 전, 연결 표시 중 전화 수신 및 부재중 알림 미표시. 당시 로그가 없어 특정 OS/기기 원인을 확정하지 않는다. 변경 기반 배터리 정책, 인증 이후 무응답 감지 부재, 일반 큐의 전화 지연 가능성, 종료 시 별도 unanswered 알림 부재를 코드에서 확인했다.
- Android app Debug/Release 빌드, JVM **38 tests** 실패/오류 0, Debug/Release lint 오류 0 / 기존 경고 각 28. 5분 주기·일치하는 응답만 인정·단일 대기 probe·세션 reset·30초 응답 한계 정책 테스트 추가. 최종 Android는 일반/우선 전송이 동일 receipt를 공유하도록 수정한 뒤 재빌드/재테스트했다. APK 2.2.1 versionCode 18, v2 서명 검증 통과.
- [macOS CI 38993477227](https://github.com/simzaekong/galaxy-relay) 성공. 검증한 iOS source f876361d95a5f09ad485888b75e2889618d17270. Swift protocol **37**, hosted 앱 **31**, 합계 **68 tests PASS**. iPhone Release/Simulator 빌드·설치·실행 성공. Swift health 정책 회귀 3개와 원래 ring 없이 도착한 unanswered 종료의 기록/표시 및 중복 억제 회귀를 추가했다.
- artifact 12086840074 ZIP SHA256 및 내장 IPA SHA256/ZIP 무결성 확인. bundle ID 유지, 2.3.1 build 16, 기존 background modes 확인, 마이크 권한 추가 없음. IPA SHA256 cadf4d3f8ff941a654569ff62b9926f72f4b158e056518182638426ac65b8b7a.
- 최종 후속 commit의 Android receipt 공유 보완은 위 로컬 검사를 통과했으며 iOS 소스는 위 CI와 같다. 전화/명령 결과는 저장 후 별도 우선 경로를 사용하고 ACK timeout 시 peer 복구를 요청한다. health timeout 자체로 위치를 측정하지 않는다.
- **실제 9시간 대기, 잠금 상태 양쪽 BLE health 왕복, One UI Doze/서비스 강제 종료, 물리 전화 수신/부재중/원격 종료는 새 버전으로 아직 검증하지 않았다.** 정책 시간 경계 테스트와 CI는 장시간 물리 수신을 보장하지 않는다. 이번 변경에서 실기기 효과를 확인했다고 주장하지 않는다. [운영/인수 절차](connection-health.md).


## iPhone 연결 카드 간소화 · 2.3.2 build 17 · 2026-10-17

- 정상 연결 문구는 상단의 초록색 ‘블루투스로 연결됨’만 표시한다. 연결된 동안 하단 ble.status 및 별도 배터리/통신 설명 행을 제거했다. Galaxy 기기 칸 안에 배터리 갱신 시간, 가운데 연결 아이콘 아래에 통신 확인 시간만 표시한다. VoiceOver 설명과 오류/연결 중 안내는 유지한다.
- [macOS CI 38994609396](https://github.com/simzaekong/galaxy-relay) 성공. iOS source 1826e019211286d1df68bf347d0f9104de508c8e. 실제 기기용 unsigned Release/Simulator 빌드·설치·실행, 기존 Swift protocol 37 + hosted 앱 31 = **68 tests PASS**. UI만 바꾸었으므로 새 구현 복제 테스트는 추가하지 않았다.
- simulator 전용 connected fixture로 Galaxy 78% 및 갱신/통신 시간을 표시한 라이트·다크 스크린샷을 내려받아 확인했다. 정상 문구 1개, 시간의 위치, 기기 칸 정렬 확인. fixture는 실제 기기 binary에 포함되지 않고 실제 BLE/설정을 저장하지 않는다. simulator iPhone 배터리 ‘—’는 시스템에서 읽을 수 없는 값이며 물리 배터리 검증으로 보지 않는다.
- artifact 12086133218 ZIP/IPA SHA256와 ZIP 무결성, bundle ID 유지, 2.3.2 build 17, 기존 background modes 확인. IPA SHA256 3bdf0db4fce430e3744b80096cbe575f9a70d372fdca187f80a5d11334ac318b.
- Android 변경 없음, 기존 2.2.1 APK를 유지한다. 통신 확인 주기/알림·통화 로직은 바꾸지 않았다. 실제 iPhone 화면과 장시간 수신 시험은 별도이며 이번 시뮬레이터 화면 확인을 물리 BLE 시험으로 간주하지 않는다.


## 오프라인 전용 앱 전달 · Android 2.3.0 / iPhone 2.4.0 · 2026-10-17

- Android app Debug/Release 빌드 성공. JVM **41 tests**, 실패/오류 0. Debug/Release lint 오류 0, 경고 각 28. APK 2.3.0 code 19 및 v2 서명 확인. 앱 선택 정책, online/unknown/disconnected 거부, 1초 상태 유효기간과 시각 역행 거부 회귀 3개 추가.
- [macOS CI 38998142690](https://github.com/simzaekong/galaxy-relay) 성공. iOS source 913889c1939e0ee52f62fbec2105abc3eb6d7e3d. Swift protocol **39** + hosted 앱 **31** = **70 tests PASS**, Release/Simulator 빌드·설치·실행 성공. network 상태 분류와 허용 wire 값/비정상 값 거부 회귀 2개 추가. 설정의 네트워크 상태 행을 시뮬레이터 화면에서 확인했다.
- artifact 12087833896의 ZIP/IPA SHA256 및 무결성, bundle ID 유지, iPhone 2.4.0 build 18, 기존 background modes 확인. IPA SHA256 6ca354b56ec8b949b5d45dfd3de44741984cd1e0cbbabff0db7ead4425c690d6.
- 후속 Android 수정: 최종 정책 함수를 실제 캡처 경로에 연결, 연결 해제 race 재확인, notificationKey별 연속 갱신 합치기/최대 256 key, 상태 query 실패의 unknown 짧은 캐시. 동일 빌드/JVM/lint를 다시 통과했다. 후속 commit은 Android 위 두 파일과 문서만 바꾸며 iOS 소스는 위 CI와 같다.
- 수신 경로 확인: 캡처 전/큐 전달 전/실제 BLE 송신 전/iPhone 저장 및 알림 표시 완료 시점에서 조건을 검사한다. 조건부 이벤트는 클라우드 pull/PWA 전달에 포함하지 않는다. 온라인 복귀로 기존 기록을 지우거나 Galaxy 원본을 취소하지 않는다.
- **실제 Galaxy·iPhone의 Wi-Fi/셀룰러 전환, 잠금/백그라운드 상태, Instagram 실제 새 알림 수신, BLE 지연/단절, 선택 UI 상호작용은 아직 실기기에서 시험하지 않았다.** 자동 정책 테스트는 이 인수 시험을 대체하지 않는다. NWPath는 개별 앱 push나 captive portal 뒤의 외부 인터넷 성공을 증명하지 않는다. [설정·제한·인수 절차](offline-apps.md).
