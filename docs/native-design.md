# iPhone 2.1.0 · 기본 iOS 디자인

사용자가 Android 2.0.7과 iPhone 2.0.4의 실제 QR/BLE 연결 성공을 확인했다. 해당 연결 수정은 유지하고 iPhone UI를 개편한다.

홈, 알림 기록, 설정의 세 탭을 시스템 TabView로 구성한다. 큰 navigation title, inset grouped List/Form, 둥근 system background 카드, 표준 toolbar 및 sheet를 사용한다. iOS 26 이상에서 Xcode 26 이상으로 빌드한 시스템 탭/툴바는 실제 Liquid Glass 디자인을 사용하며 주요 버튼은 glassProminent를 사용한다. iOS 16~18에서는 기본 시스템 컨트롤과 borderedProminent로 호환한다. 읽기용 카드 전체에 가짜 유리나 배경 이미지를 덧씌우지 않는다. 지도/위치 권한 및 측정하지 않은 배터리 수치는 추가하지 않는다.

알림은 기존 앱/대화 묶기를 유지한다. 묶음 탭으로 펼치기, 왼쪽 swipe로 Galaxy 및 로컬 알림 제거, 상세의 본문을 다시 눌러 원본 Galaxy 알림 열기, RemoteInput 답장도 유지한다. 상세 확인은 read만 변경하며 기록을 삭제하지 않는다. Galaxy 원본 제거 이벤트는 로컬에서도 삭제한다.

## 앱 아이콘

Android 2.0.8은 nativeMode일 때 PackageManager의 실제 application icon을 48/32/24 pixel JPEG로 줄여 1,536 byte 이하로 보낸다. 인터넷 이미지 다운로드, 주소록 수집 및 추가 권한은 사용하지 않는다. package lastUpdateTime을 키로 최대 64개 메모리 캐시를 사용한다. appIcon은 기존 AES-GCM 보호 이벤트에 포함되고 서버에서 복호화하지 않는다. cloud batch는 6개 이하 및 JSON 30,000 byte 이하로 제한해 기존 ciphertext 제한을 지킨다.

iPhone은 base64 길이/데이터 크기/이미지 dimensions를 확인하고 64×64 이하만 디코딩한다. 최대 128개 메모리 캐시를 사용한다. 기존 알림의 optional appIcon이 없어도 기존 기록을 읽으며 기본 SF Symbol로 표시한다. 원본 아이콘은 업데이트 후 새로 생성된 알림부터 표시한다. 저용량 전송을 위해 손실 압축하며 아이콘을 얻지 못하면 알림 자체는 계속 전달한다.

기존 로컬 기록 로더가 네트워크 패킷과 같은 33,000 byte 복호화 제한을 사용하던 문제도 수정한다. 아이콘이 포함되면 적은 개수의 기록만으로 이를 초과할 수 있었다. 로컬 암호화 기록에는 별도 48 MiB 한도를 적용하고 읽기 전에 파일 크기를 70 MiB 이하로 확인한다. 네트워크 패킷의 기본 33,000 byte 제한은 유지한다. 기존 암호화 파일 형식과 Keychain 키는 바꾸지 않는다. 재실행 후 다수 아이콘 기록을 복원하고, 네트워크의 큰 패킷은 계속 거부하는 회귀 테스트를 추가한다.

앱 내부 아이콘과 잠금화면 발신 앱 아이콘은 다르다. iOS의 일반 local notification은 Galaxy Relay의 앱 정체성으로 표시하며 다른 앱을 발신 앱처럼 꾸미지 않는다. 알림 attachment는 이 앱 아이콘을 대체하지 않으므로 이 변경에서는 붙이지 않는다. native notification 제목/본문은 기존 원본 정보 표시를 유지한다.

## 검증

Android build/JVM 회귀/lint, iOS protocol 및 hosted app tests, iPhone/Simulator build와 실제 simulator launch를 수행한다. AppearanceTests는 아이콘 없는 이전 데이터 호환, 정상 이미지 round trip, 큰 데이터/큰 dimensions/잘못된 이미지 거부, 상세를 읽어도 기록 유지 및 Galaxy 제거 동기화를 검증한다. CI는 라이트/다크 화면을 캡처한다. `--design-preview` 예시 데이터는 simulator compile에만 포함되며 실제 iPhone IPA에는 포함하지 않는다. 물리 기기 아이콘 전송과 화면 확인은 별도 실기기 검증이 필요하다.

Android Debug/서명 Release build, JVM 19개 및 Debug/Release lint 오류 0개 확인. Xcode 26.3/iOS 26.2 SDK에서 protocol 6개, hosted app 9개(새 appearance/history 4개 포함), iPhone/Simulator build 및 simulator 실행/4종 디자인 화면 캡처 성공. 첫 실행에서 artifact 업로더가 숨김 출력 디렉터리 .build-ipa를 제외한 것을 확인해, 명시한 IPA/checksum/PNG에 한해서 hidden file 포함을 허용하고 파일이 없으면 CI가 실패하도록 수정했다.

참고: [Apple · Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass), [SwiftUI custom glass](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views), [UNNotificationContent attachments](https://developer.apple.com/documentation/usernotifications/unnotificationcontent/attachments).
