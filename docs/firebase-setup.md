# Firebase Cloud Messaging 설치

FCM은 iPhone의 OPEN/DISMISS/ACK/PING/SETTINGS_SYNC 명령을 Galaxy로 전달하는 채널이다. 메시지 본문·전체 알림 기록을 Firebase database에 저장하지 않는다. Realtime Database/Firestore/Functions는 사용하지 않는다.

## 개인 계정 준비

1. [Firebase Console](https://console.firebase.google.com/)에서 무료 Spark 프로젝트를 만든다. 이번 설치의 ID는 `galaxy-relay-example`이다. Analytics는 필요하지 않다. Billing 연결은 필요하지 않다.
2. 프로젝트 설정 → 일반 → Android 앱 추가. Package `kr.infosphere.galaxyrelay`를 입력한다. 기본 FCM 토큰 발급에는 SHA 인증서 등록이 필요하지 않다.
3. `google-services.json`을 내려받아 `android/app/google-services.json`에 저장한다. Package와 Gradle applicationId가 같아야 한다. Gradle Google Services plugin은 파일이 있을 때 적용한다.
4. 프로젝트 설정 → Cloud Messaging에서 HTTP v1 API 사용 가능 상태를 확인한다. 필요하면 Google Cloud API Library의 **Firebase Cloud Messaging API**를 활성화한다. Legacy server key API를 사용하지 않는다.
5. Google Cloud IAM → 서비스 계정에서 전용 `relay-fcm` 계정을 생성한다. 이 프로젝트의 `roles/firebasecloudmessaging.admin`을 부여한다. Owner/Editor 권한을 주지 않는다.
6. 해당 서비스 계정의 JSON key를 생성하여 로컬 `.secrets/fcm-service-account.json`에 저장한다. 이 private key는 Android나 PWA에 넣지 않는다.

CLI를 선호하면 자신의 project ID와 서비스 계정 ID로 아래를 실행한다.

```sh
gcloud services enable fcm.googleapis.com iam.googleapis.com --project=YOUR_PROJECT_ID
gcloud iam service-accounts create relay-fcm --project=YOUR_PROJECT_ID
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID --member=serviceAccount:relay-fcm@YOUR_PROJECT_ID.iam.gserviceaccount.com --role=roles/firebasecloudmessaging.admin
gcloud iam service-accounts keys create .secrets/fcm-service-account.json --iam-account=relay-fcm@YOUR_PROJECT_ID.iam.gserviceaccount.com --project=YOUR_PROJECT_ID
```

첫 명령 전 올바른 개인 계정/project인지 확인한다. 운영 계정 정책이 JSON key 생성 자체를 금지하면 그 조직 정책을 우회하지 말고 키 발급이 허용된 개인 프로젝트를 사용한다.

## Cloudflare 설정

```sh
cd backend
node scripts/import-fcm.mjs
npx wrangler secret bulk ../.secrets/fcm-worker.json
```

스크립트가 client_email/private_key만 Worker secret 형식으로 옮긴다. 콘솔에 키를 출력하지 않는다. `wrangler.jsonc`의 `FCM_PROJECT_ID`도 동일 프로젝트로 변경하고 배포한다. Worker는 짧은 RS256 OAuth assertion으로 `firebase.messaging` scope access token을 받은 후 HTTP v1 `/messages:send`를 호출한다.

## Android 연결·확인

Android Studio에서 Sync → `:app:assembleDebug` → APK 설치 → Backend 등록을 진행한다. FCM token은 Google Play services가 제공한다. 신규 token이 생성되면 Android가 암호화 저장하고 Backend 갱신을 큐/앱 재개 시 재시도한다. 에뮬레이터는 Play services 포함 image를 사용한다.

Pairing 및 Web Push 활성화 후 PWA의 **Galaxy 연결 테스트**로 실제 PONG 응답을 확인한다. API의 accepted는 FCM이 요청을 수락했다는 뜻이며 Galaxy 실행 완료는 별도 command_result receipt를 PWA가 조회해 확인한다. 결과 Push 배너는 표시하지 않는다.

FCM high priority TTL은 120초다. Doze·FCM quota·알림 권한·네트워크에 따라 지연/실패할 수 있다. 앱 강제 종료 상태에서는 사용자가 Relay를 다시 실행해야 한다. 비밀 키 교체 시 새 key를 Worker secrets에 등록하고 정상 전송을 확인한 뒤 이전 key를 IAM에서 삭제한다.

공식 근거: [HTTP v1 인증/전송](https://firebase.google.com/docs/cloud-messaging/send/v1-api), [Android 수신](https://firebase.google.com/docs/cloud-messaging/android/receive), [Firebase 요금](https://firebase.google.com/pricing).
