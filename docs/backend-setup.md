> 공개 스냅샷 안내: 과거 배포·빌드 기록입니다. 운영 주소와 다운로드는 제공하지 않으며, 현재 상태와 설치 전제는 [공개 설정 안내](public-source.md)를 먼저 확인하세요.

# Cloudflare / Firebase 설치

## 이 환경에서 생성한 자원

- Worker: `galaxy-relay-example`
- URL: https://relay.example.com
- KV: `galaxy-relay-state-example`
- Firebase / Google Cloud project: `galaxy-relay-example`
- Android app ID: `kr.infosphere.galaxyrelay`
- FCM sender service account: `relay-fcm` (Firebase Cloud Messaging API Admin 역할)

기존 프로젝트나 Worker를 덮어쓰지 않고 별도 자원을 생성했다. Firebase billing을 연결하지 않았다. Cloudflare Free 설정을 사용한다. Custom Domain은 선택이며 기존 도메인이 없으면 구매하지 않아도 workers.dev URL로 사용할 수 있다.

## 처음 설치하는 계정

Node.js 22 이상을 설치하고 repository의 `backend`에서 아래 순서로 실행한다. PowerShell에서는 npm/npx 대신 npm.cmd/npx.cmd를 사용할 수 있다.

```sh
npm ci
npx wrangler login
npx wrangler kv namespace create galaxy-relay-state
```

새 namespace ID를 `wrangler.jsonc`의 STATE id에 적는다. Worker name도 자신의 고유 이름으로 변경한다. `compatibility_date`는 설치일 기준 지원 날짜로 선택한다.

Firebase Console https://console.firebase.google.com/ 에서 무료 Spark 프로젝트를 생성한다. Android 앱을 `kr.infosphere.galaxyrelay`로 등록하고 `google-services.json`을 내려받아 `android/app/`에 저장한다. Firebase Cloud Messaging API(HTTP v1)를 Google Cloud Console에서 활성화한다. 별도 전용 서비스 계정을 만들고 `roles/firebasecloudmessaging.admin`만 부여한다. 전체 Owner/Editor 권한을 Worker 서비스 계정에 부여하지 않는다. Google Cloud IAM → 서비스 계정 → 키에서 JSON을 생성하고 안전하게 보관한다.

`wrangler.jsonc`의 FCM_PROJECT_ID를 자신의 Firebase project ID로, VAPID_SUBJECT를 실제 운영자 연락처 `mailto:...`로 변경한다.

```sh
node scripts/prepare-secrets.mjs
npx wrangler secret bulk ../.secrets/cloudflare.json
```

VAPID key 및 INSTALL_SECRET/COMMAND_SECRET이 `.secrets/cloudflare.json`에 만들어진다. 이 파일은 Git에서 제외한다. INSTALL_SECRET은 Android 첫 등록 화면에서만 사용한다. 내용을 채팅·로그·이슈에 복사하지 않는다. 스크립트는 기존 비밀이 있으면 덮어쓰지 않는다.

서비스 계정 JSON을 `.secrets/fcm-service-account.json`에 저장한 뒤:

```sh
node scripts/import-fcm.mjs
npx wrangler secret bulk ../.secrets/fcm-worker.json
npm run types
npm run check
npm test
```

VAPID_PRIVATE_KEY, FCM_PRIVATE_KEY, FCM_CLIENT_EMAIL, INSTALL_SECRET, COMMAND_SECRET은 Worker secrets로 관리한다. VAPID_PUBLIC_KEY도 같은 설치 파일에서 공급한다. 서버는 공개 VAPID 키만 `/api/config`로 제공한다.

PWA를 먼저 빌드하고 배포한다:

```sh
cd ../web
npm ci
npm run build
cd ../backend
npx wrangler deploy --dry-run
npm run deploy
```

처음부터 Backend만 개발할 때는 assets 설정을 잠시 빼고 API 단독 테스트를 할 수 있다. 최종 repository는 PWA 빌드 후 배포하도록 구성되어 있다.

```sh
npm run dev
```

로컬 API: http://127.0.0.1:8787/api/status . 모바일 앱은 HTTPS만 허용하므로 Android 연동에는 배포된 HTTPS URL을 사용한다. 실제 secrets를 로컬에 넣어 테스트하려면 Git에서 제외된 `.dev.vars`에 설정한다. 테스트용 endpoint를 production에 추가하지 않는다.

## Custom Domain

Cloudflare Dashboard → Workers & Pages → 해당 Worker → Settings → Domains & Routes → Add → Custom Domain에서 이미 보유한 Cloudflare 관리 도메인을 연결한다. HTTPS 발급 후 Android Backend 주소를 해당 origin으로 설정한다. PWA origin 변경은 별도 IndexedDB·Push subscription을 생성하므로 기존 연결을 해제한 뒤 새 origin에서 페어링한다.

## 비용·제한

목표는 무료 한도 안에서 월 0원이다. [Workers 제한](https://developers.cloudflare.com/workers/platform/limits/)과 [KV 무료 한도](https://developers.cloudflare.com/kv/platform/limits/)를 확인한다. 확인 시 Free KV write/delete/list 각각 1,000/day, read 100,000/day, 동일 key 쓰기 1/sec다. 이벤트마다 KV counter, history, lastSeen을 쓰지 않는다. heartbeat는 10분 이상 간격일 때만 저장한다. Rate Limiting binding은 지역 단위 제한이며 전역 정확한 counter는 아니다. 대량 공격·무료 한도 초과 시 오류가 발생할 수 있으며 자동 유료 전환을 구성하지 않았다.

FCM 전송과 iPhone Web Push는 무료다. Google Cloud의 다른 유료 API, Database, Firebase Functions를 추가할 필요가 없다. 사용자 네트워크·기기 비용과 선택적인 도메인 구매 비용은 월 서비스 비용 목표에 포함하지 않는다.
