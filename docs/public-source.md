# 공개 소스 설정

공개본은 새 Git 이력으로 시작합니다. 과거 비공개 PR·Actions 실행·로컬 artifacts는 옮기지 않았습니다. 다른 문서의 과거 검증 기록은 참고 자료입니다. 공개본의 README와 이 안내를 먼저 적용합니다.

`backend/wrangler.jsonc`의 Worker 이름, `STATE.id`의 0으로 된 예시 namespace ID, `FCM_PROJECT_ID`, `VAPID_SUBJECT`를 자신의 값으로 바꾸세요. 예시 ID로 운영 배포하지 마세요. 생성된 `worker-configuration.d.ts`도 예시 기준이며 설정 후 `npm run types --prefix backend`로 갱신합니다. 실제 운영 설정은 로컬에서 관리하고 공개 커밋에 포함하지 마세요. 저장소 공개 작업은 기존 서버 배포나 기기 연결을 변경하지 않습니다.

Android 최초 등록 화면의 서버 주소는 비어 있습니다. 자신의 배포 주소를 입력합니다. `google-services.json`, keystore, signing properties 및 `.secrets/`는 Git 제외 대상입니다. 기존 개인 배포 APK의 서명 키는 제공하지 않습니다.

실제 클라우드·에뮬레이터 통합 시험에는 환경 변수 `RELAY_BACKEND_URL`을 자신의 테스트 서버로 지정하세요. ADB는 `ADB_PATH`가 있으면 사용하고, 없으면 PATH의 `adb`를 사용합니다. 이 도구들은 테스트 앱 등록·데이터 생성·정리 작업을 수행하므로 본인의 격리된 시험 환경에서 사용합니다. 합성 데이터 기반 카메라 테스트는 운영 서버를 사용하지 않습니다.

두 Actions workflow는 `workflow_dispatch`만 지원합니다. push·PR·예약 실행은 없으며 읽기 권한만 요청합니다. 외부 action은 검토 시 조회한 전체 commit SHA로 고정했습니다. 원격 배포·서명 비밀값은 주입하지 않습니다. 실제 실행은 소유자의 수동 요청으로 시작합니다.

재배포 권한의 출처를 확인하지 못한 구형 `wallpaper.png`는 제외했습니다. 현재 PWA 빌드에서는 이미 제외하던 이미지이며 `docs/liquid-glass.md`와 구형 preview 도구는 역사적 구현 설명입니다. 해당 렌더러를 재사용하려면 직접 소유한 대체 이미지를 준비해야 합니다.

공개 저장소 열람과 소스 재사용 허가는 별개입니다. 별도의 오픈소스 라이선스는 부여하지 않았습니다. [권리 안내](../COPYRIGHT.md)를 확인하세요.
