# iPhone 2.7.0 검증 — 2026-10-25

[공개 CI 실행](https://github.com/simzaekong/galaxy-relay/actions/runs/40167195779)이 성공했습니다. 검증한 소스 커밋은 `86d922fbf13324ad93951574f7884c58a1b7aa2a`입니다.

- Swift 프로토콜 테스트 47개 및 iOS 앱 테스트 44개 통과, 실패 0개.
- 앱 테스트에는 카메라 닫기 중복 방지, 요청 대기 중 종료·늦은 응답 취소, 서버 실패, 원격 종료 이후 상태 변경 차단 4개가 포함됩니다.
- iPhone 실기기용 Release 컴파일, unsigned IPA 패키징, 시뮬레이터 Release 실행과 Debug 테스트, 화면 캡처, 아티팩트 업로드 통과.
- 홈·리모트의 카메라 실행 진입점과 기존 화면의 라이트·다크 캡처 확인.
- 내려받은 ZIP의 GitHub digest와 내부 IPA의 SHA-256 대조, ZIP 무결성, arm64 실행 파일, 버전 2.7.0 / build 22 및 앱 식별자 확인. 알려진 로컬 비밀값 미검출.

## 받기와 설치

위 CI 페이지의 `GalaxyRelay-unsigned-IPA` 아티팩트 ZIP에 IPA와 화면 캡처가 있습니다. GitHub 로그인과 권한이 필요할 수 있으며 이 실행의 보관 만료는 2026-10-28 06:02 UTC입니다. 재빌드는 수동 workflow 실행으로 시작합니다. 저장소 소스 자체에는 APK/IPA를 커밋하지 않습니다.

IPA SHA-256: `304069b6eba032f8de71b4c081bef928d66be66af558df735dcbf4201cec7387`

앱 식별자는 `kr.infosphere.galaxyrelay.ios`입니다. unsigned IPA이므로 AltStore 등에서 본인 계정으로 서명한 뒤 설치합니다. 카메라 기능에는 Galaxy 2.5.0 이상 및 해당 서버 API가 필요합니다. Galaxy 설정의 **내 iPhone 카메라 요청 자동 수락**을 켜고 필요한 권한을 허용합니다. 자신의 페어링 QR과 서버를 사용하세요.

## 검증의 범위

실제 iPhone WKWebView ↔ Galaxy WebView 영상 연결, 실제 사진·동영상 파일 저장, One UI의 자동 화면 열기, 실기기 잠금·백그라운드 전환 동작은 이 CI로 검증하지 않았습니다. 가상 peer 테스트 및 모델 테스트가 실기기 인수 검증을 대신하지 않습니다. [원격 카메라 동작과 제한](remote-camera.md)을 확인하세요.

첫 공개 실행은 테스트 통과 후 화면 캡처 도중 수동 중단되어 IPA를 업로드하지 못했습니다. workflow의 시뮬레이터 컴파일·부팅·테스트·캡처 단계를 분리한 뒤 위 실행에서 전체 완료를 확인했습니다. 앱 동작 코드는 이 재실행을 위해 변경하지 않았습니다.
