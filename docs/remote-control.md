# 리모트와 내 Galaxy 찾기

배포 버전: Android 2.4.0 (20), iPhone 2.5.1 (20). [PR #18](https://github.com/simzaekong/galaxy-relay)을 main에 병합했고 운영 Worker 배포를 완료했다. 양쪽 앱을 기존 앱 위로 업데이트한다. 서버 위치 찾기는 Galaxy에서 별도 기능 허용과 위치 항상 허용이 필요하다.

## 사용 흐름

- 인증된 BLE 연결이 완료되면 iPhone 하단에 **리모트** 탭이 나타난다. 끊기면 홈으로 돌아가며 탭을 숨긴다.
- 음악: 현재 제어 대상 앱·곡·아티스트, 재생/일시 정지, 이전/다음 곡, 미디어 음량. 활성 미디어 세션이 없으면 재생 버튼을 끈다. 현재 재생 중인 세션을 우선하며 다른 앱을 임의 실행하지 않는다.
- 벨소리: 벨소리 음량, 무음/진동/소리. Android 방해금지 접근 또는 기기 정책에 따라 거부될 수 있다. Galaxy 설정의 iPhone 리모트에서 해당 권한 설정 화면을 연다.
- 버튼을 누르면 아이콘·선택 색·음량을 먼저 갱신한다. 한 번에 한 조작만 보내고 900ms 뒤 읽은 실제 상태로 확인한다. 실패·불일치·5초 응답 지연이면 복원한다. 재생·일시 정지/음량/모드/찾기 소리는 해당 실제 값을 확인한다. 이전/다음은 트랙 변경 또는 이전 곡 위치 초기화가 관측되지 않으면 확인 실패로 처리한다.
- 음량은 Galaxy 하드웨어 단계로 정규화해 확인한다. 새 명령을 대기열이나 서버에 보관하거나 연결 복구 후 재전송하지 않는다. 요청 ID와 BLE 세대가 다른 응답은 적용하지 않는다.
- 리모트 탭이 활성화되고 iPhone 화면이 켜진 동안에만 2초 간격 RSSI, 4초 간격 상태 조회. 앱 백그라운드와 탭 이탈 시 추가 조회를 멈춘다.

## iPhone 2.5.1 자동 조회 중 터치 수정

2.5.0은 4초 간격 상태 조회도 조작 대기로 처리해 전체 버튼을 잠그고 확인 중 행을 삽입/제거했다. 이 때문에 주기적으로 화면이 바뀌고 터치할 수 없는 구간이 생겼다.

2.5.1은 자동 조회 ID/timeout과 사용자 조작을 분리한다. 자동 조회 중에는 버튼을 잠그거나 로딩 상태를 내보내지 않는다. 조작을 누르면 이전 조회 ID를 무효화해 늦은 snapshot이 낙관적 표시를 덮어쓰지 않게 한다. 조작 대기 중에는 추가 조회를 생략하며, 조회 지연 시 이미 읽은 상태와 사용 가능한 버튼을 유지한다. 동일한 snapshot은 화면 상태를 다시 publish하지 않는다. 상태 안내 행은 항상 같은 위치에 두고 내용만 바꾼다. 실제 조작의 실패 복원 및 5초 응답 제한은 유지한다.

회귀 테스트는 반복 조회 중 잠금/불필요한 publish 방지, 조회 중 터치 우선, 늦은/잘못된 응답 무시, 조회 지연 시 기존 상태 유지, 조작 timeout 복원 및 재조회, 거절 응답 복원, 상태 변화 반영, 단절 후 이전 응답 거부를 검증한다. 서버와 Android 변경은 필요 없다. [2.5.1 CI](https://github.com/simzaekong/galaxy-relay)에서 core 47개 + 앱 40개 = 총 87개 테스트가 통과했다. 추가 회귀는 core 1개와 RemoteModelTests 9개다. iPhone/Simulator 빌드·실행, 리모트 라이트/다크 화면, IPA 체크섬·버전 2.5.1 build 20을 확인했고 [PR #19](https://github.com/simzaekong/galaxy-relay)를 main에 병합했다.

## 가까운 곳에서 찾기

BLE 연결 중 소리 시작/중지. 기본 알람 소리를 최대 15초 재생하고 BLE 단절/서비스 종료 때 중지한다. 음량을 강제로 최대값으로 바꾸거나 방해금지를 우회하지 않는다. 실제 소리 크기와 출력 장치는 Galaxy 시스템 정책에 따른다.

RSSI는 유효한 음수 dBm만 사용하며 최근 4개 평균을 표시한다. 앞 4개와 뒤 4개 평균 차이가 4dB 이상이면 강해짐/약해짐, 그 외 비슷함. 처음 8개가 모일 때까지 비교 중을 표시한다. 8초 이상 된 표시는 측정 대기로 바꾸고 과거 샘플을 초기화한다. 벽·몸·기기 방향·간섭의 영향을 받으므로 거리(m), 방향 화살표, 실제 접근 여부로 표현하지 않는다.

## BLE 없이 서버로 현재 위치 찾기

홈의 **내 Galaxy 위치 찾기 → 서버로 현재 위치 요청**은 BLE 없이 사용한다. Galaxy 설정 → iPhone 리모트 → **서버를 통한 내 기기 위치 찾기**를 켜고 위치를 **항상 허용**해야 한다. 기능은 기본 꺼짐이다. 기존 연결 해제 위치 기록과 독립된 동의이며, 기존 기능을 켰다는 이유로 원격 위치를 제공하지 않는다.

1. iPhone 전용 인증으로 `/api/native/locate/start`를 요청한다.
2. Worker는 요청 ID·양쪽 기기 ID·만료 시각을 묶은 `native-locate/v1` HMAC grant를 발급한다. 기존 Android commandKey로 서명한 `NATIVE_LOCATE`를 FCM으로 보낸다. 대상 nativeDeviceId도 서명에 포함한다.
3. Galaxy는 명령 서명·freshness·nonce/command 중복·대상 iPhone·현재 페어링·기능 설정·백그라운드 위치 권한을 확인한다. 위치 foreground service에 조회 중 알림을 표시한다.
4. 한 번의 위치 요청을 최대 20초 수행한다. 위치 서비스 꺼짐/권한 없음/백그라운드 시작 제한/현재 위치 없음은 명시적 오류 응답으로 구분한다. 30초보다 오래된 위치는 보내지 않는다.
5. 좌표·정확도·측정 시각 또는 실패 상태를 기존 linkKey로 AES-GCM 암호화한다. AAD는 `GalaxyRelay/native-location/v1\nrequestId\nnativeDeviceId`다. 알림 cloud pull AAD와 분리한다.
6. `/locate/upload`는 현재 페어의 Android 인증과 grant가 일치하는 암호문만 최대 120초 보관한다. iPhone은 4초 간격으로 최대 24회 조회하며 실제 만료 시각도 확인한다. 복호화·요청 ID·응답/측정 시각·좌표 범위를 검증한 뒤 지도 버튼을 제공한다. ACK로 서버 암호문을 삭제한다. ACK 실패 시 TTL로 만료한다.

위치 원문·위치 이력을 서버나 로컬 이벤트 큐에 저장하지 않는다. 결과는 iPhone 메모리에만 유지하고 새 조회/페어링 해제 때 지운다. 지도에는 측정 시각·정확도를 함께 보여준다. 위치 조회/업로드가 끝나면 서비스를 종료하며 사용자 연결 해제 때에도 중지한다.

양쪽 인터넷, FCM 수신, Galaxy 위치 서비스와 권한이 필요하다. 전원 꺼짐·강제 중지·OEM 절전·FCM 지연·백그라운드 서비스 제한은 원격으로 우회하지 않는다. KV의 eventual consistency로 응답이 늦을 수 있으며 장기 polling을 하지 않는다. 지원하지 않는 기존 서버는 업데이트 안내를 표시한다.

## 검증과 남은 확인

- 서버 TypeScript 검사 통과. Vitest 22개 통과: 기존 회귀 및 위치 요청 서명/역할/다른 페어 접근/변조/평문 거부/다른 grant 도메인 거부/만료/TTL/ACK/페어링 폐기.
- Android Debug·Release 빌드, JVM 43개 테스트, Debug·Release lint 오류 0개 통과. 추가한 명령 인증 테스트는 위치 grant와 대상 이벤트가 서명으로 보호됨을 확인한다.
- Swift 테스트 7개 추가(위치 암호문 컨텍스트 분리 포함): 낙관적 표시·실패 복원·성공 유지·지연 응답 무시·요청 중복 차단·음량 단계·RSSI 비교·단절 초기화. [macOS CI 40087104929](https://github.com/simzaekong/galaxy-relay)에서 protocol 46개와 hosted app 31개, 총 77개 테스트가 통과했다. iPhone/Simulator 빌드·실행과 리모트 라이트/다크 화면 캡처를 확인했고 IPA 체크섬·앱 버전·번들 ID·background modes를 검증했다.
- 운영 배포: Worker version `f5b9964f-f396-446c-9193-6653c73223b5`. 별도 합성 기기로 기존 health, 위치 API/FCM 선행 조건, 암호문 왕복·복호화, 역할/다른 페어/평문/변조/만료 거부, ACK를 검증하고 테스트 기기 인증을 폐기했다. 실제 기기에 FCM이나 위치 요청을 보내지는 않았다. 재검증은 `node tools/test-location-cloud.mjs --production`으로 수행한다.
- 실기기 미검증: 잠긴 Galaxy에서 실제 음악 앱 제어, 음량·벨소리/방해금지 조합, 소리 자동 종료, 거리·벽·방향에 따른 RSSI, BLE 단절 직후 소리 중지, background FCM 위치 요청/정확도/권한 취소, 페어링 해제와 요청의 동시 발생.

참고: [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [AudioManager](https://developer.android.com/reference/android/media/AudioManager), [Android background service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Core Bluetooth RSSI](https://developer.apple.com/documentation/corebluetooth/cbperipheral/readrssi()).

## iPhone 2.6.0 화면 구성

리모트는 음악 카드, 이전/재생/다음 버튼, 세로 미디어 음량, 원형 무음/진동/소리 버튼, 가로 벨소리 음량으로 구성한다. 앨범 이미지를 전송하는 기능은 아직 없으므로 음악 아이콘을 표시한다. 라이트/다크 테마에 대응하며 접근성 글자 크기에서는 미디어 음량을 가로로 배치한다. 음량은 드래그 중 로컬 초안만 바꾸고 손을 뗄 때 한 번 요청한다. 서버/Android 프로토콜과 기존 낙관적 표시·상태 확인·실패 복원 정책은 유지한다.

홈에는 기기 찾기 카드 하나로 BLE 소리 울리기, RSSI 비교, 서버 현재 위치 요청을 모았다. BLE가 연결되지 않은 동안만 마지막 연결 해제 위치를 같은 카드 안에서 보여준다. RSSI는 홈을 보고 앱이 활성화된 동안만 읽는다. 리모트는 상태 조회만 하며 RSSI는 읽지 않는다.

홈의 최근 알림 목록을 제거하고 안 읽은 알림/오늘 받은 알림 카드를 알림 화면 상단으로 옮겼다. 탭 및 화면 제목은 알림으로 변경했다. 기존 알림 기록·검색·묶음·상세·답장·삭제는 유지한다. iPhone 2.6.0 build 21만 업데이트하며 Android 및 운영 서버 변경은 없다.
