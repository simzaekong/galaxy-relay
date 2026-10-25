# Galaxy ↔ iPhone Relay

Galaxy 알림을 iPhone 네이티브 앱으로 전달하고, iPhone에서 Galaxy의 음악·음량·벨소리 모드와 기기 찾기를 조작하는 개인용 프로젝트입니다. Android Java, iOS SwiftUI/CoreBluetooth, Cloudflare Workers/KV/Durable Objects로 구성합니다.

공개용 설정으로 정리한 최신 소스 스냅샷입니다. 기존 비공개 개발 이력, Actions 로그, 설치 파일, 운영 서버 설정·비밀값·서명 키는 포함하지 않습니다. 아래 문서의 과거 배포·실행 결과는 개발 당시 기록이며 이 공개 저장소에서 재실행한 결과가 아닙니다.

## 현재 상태

- BLE 우선 알림 전달·답장·제거, 음악/음량/벨소리 리모트, 소리·RSSI·서버 위치 찾기 구현.
- 원격 카메라 소스 포함: Galaxy → iPhone 단방향 WebRTC, 양쪽 마이크 및 iPhone 카메라 미사용. STUN 기반 직접 연결이며 TURN 중계는 사용하지 않습니다. 네트워크에 따라 연결되지 않을 수 있습니다.
- 카메라용 Android 2.5.0 및 서버는 비공개 개발 환경에서 검증했습니다. iPhone 2.7.0은 빌드·실기기 검증 전입니다. 마지막 검증 iPhone 버전은 2.6.0이며 카메라 기능은 포함하지 않습니다. 이 저장소에는 APK/IPA가 없습니다.
- Galaxy 카메라는 사전 동의와 권한이 필요합니다. Android가 자동 화면 실행을 차단하면 Galaxy에서 알림을 눌러야 합니다. 종료·백그라운드 전환 시 세션을 닫고, 비정상 연결 종료에는 임대 시간 제한을 적용합니다. [카메라 동작과 제한](docs/remote-camera.md).
- iOS 강제 종료·서명 만료·BLE 단절 중 닫힌 앱에 대한 상시 수신은 보장하지 않습니다. [구조](docs/native-architecture.md).

## 직접 설치·빌드

1. [공개본 설정 안내](docs/public-source.md)에 따라 자신의 Cloudflare/Firebase 자원과 설치 비밀값을 준비합니다. 저장소의 서버 주소·메일·namespace ID는 예시입니다.
2. [Backend 설치](docs/backend-setup.md), [Firebase 설정](docs/firebase-setup.md), [Android 설정](docs/android-setup.md)을 진행합니다. 기존 개발자의 서명 키는 제공하지 않으므로 기존 개인 배포 앱을 같은 서명으로 덮어 설치할 수는 없습니다.
3. [iPhone 설치·빌드](docs/native-ios-setup.md)를 참고합니다. GitHub Actions의 `Build native iPhone IPA`를 수동 실행하면 unsigned IPA를 빌드하도록 구성되어 있습니다. 소유자가 실행하기 전에는 자동 실행되지 않습니다. 이 공개본에서 빌드가 성공했다고 검증한 상태는 아닙니다.
4. Galaxy의 QR을 iPhone에서 스캔합니다. QR과 설치 비밀값은 공유하지 않습니다.

```sh
npm ci --prefix backend
npm run check --prefix backend
npm test --prefix backend
npm ci --prefix web
npm test --prefix web
npm run build --prefix web
```

## 구성

| 디렉터리 | 내용 |
| --- | --- |
| `android/` | Galaxy BLE/FCM 앱, 테스트용 별도 앱, Gradle Wrapper |
| `ios/` | SwiftUI 앱·RelayCore·테스트·XcodeGen 설정 |
| `backend/` | 인증·암호문 전달·FCM 요청·카메라 연결 정보 교환 |
| `web/` | 기존 PWA 및 카메라 WebRTC 클라이언트 |
| `docs/` | 설치·프로토콜·제한·과거 개발 검증 기록 |
| `tools/` | 테스트·패키징 도구. 클라우드 통합 시험에는 자신의 서버를 지정 |

[리모트·찾기](docs/remote-control.md) · [BLE 복구](docs/ble-recovery.md) · [보안 안내](SECURITY.md) · [권리 및 외부 구성요소](COPYRIGHT.md)
