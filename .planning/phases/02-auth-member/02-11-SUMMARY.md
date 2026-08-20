---
phase: 02-auth-member
plan: 11
subsystem: auth
tags: [validation, openapi, error-codes, boot4, kakao-oauth, manual-e2e]

# Dependency graph
requires:
  - phase: 02-auth-member (02-01~02-10)
    provides: 카카오 로그인·JWT 발급/회전·관리자 인증·온보딩·회원 승인/상태변경 전 기능 + 각 플랜의 문서·계약 산출물
provides:
  - "Phase 2 전체 테스트 스위트 green 확정(200개, 실패 0) — `./gradlew ktlintFormat && ./gradlew build`"
  - "docs/api/openapi.yaml이 실제 API와 1:1 일치함을 재생성·diff로 확정(드리프트 없음, 11개 경로 전부 존재, 테스트 전용 경로 미노출)"
  - "ErrorCode enum ↔ docs/error-codes.md ↔ glossary.md 3자 정합 확정"
  - "docs/conventions.md §11에 Phase 2에서 실제 확인한 Boot4 차이 4건 추가(@MockitoBean, RestClient.Builder 부재, Jackson 어노테이션 패키지 비대칭, RestClient.requiredBody)"
  - ".planning/phases/02-auth-member/02-VALIDATION.md 확정(TBD 제거, Per-Task Verification Map 12행 실값 채움, nyquist_compliant/wave_0_complete/status 갱신)"
  - "실제 카카오 계정 E2E(로그인→온보딩→관리자 승인→강제 로그아웃) 사람 검증 완료 — 카카오 콘솔 설정이 실제로 유효함을 최초로 증명"
  - "AdminAuthService 트랜잭션 버그 발견(수동 검증에서만 드러남) — 메인 트리 커밋 05241f5로 수정 완료"
affects: [Phase 3(이용권) 이후 — Phase 2 인증·회원 기반이 검증 완료 상태로 확정됨]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "목킹 테스트 통과와 실제 서버 기동 후 수동 E2E는 서로 다른 결함 계층을 잡는다 — 테스트 트랜잭션(쓰기 가능)이 가려온 read-only 트랜잭션 버그가 실제 bootRun에서만 드러남"

key-files:
  created:
    - .planning/phases/02-auth-member/02-11-SUMMARY.md
  modified:
    - docs/conventions.md
    - .planning/phases/02-auth-member/02-VALIDATION.md

key-decisions:
  - "새 D 번호 없음 — 이 플랜은 새 기능을 만들지 않고 기존 산출물의 정합성만 확인·문서화했다. 유일한 코드 수정(AdminAuthService 트랜잭션 버그)은 메인 워크트리에서 별도로 처리되어 이 워크트리 산출물엔 포함되지 않는다"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, MEMBER-01, MEMBER-02, MEMBER-03, MEMBER-04]

# Metrics
duration: ~60min (체크포인트 대기 시간 제외)
completed: 2026-08-03
---

# Phase 2 Plan 11: Phase 마감 — 전체 검증·문서 정합·실제 카카오 E2E Summary

**Phase 2(인증·회원)를 새 기능 없이 닫는 마감 플랜. 200개 테스트 전체 green, openapi.yaml 드리프트 없음, ErrorCode/glossary/conventions 3자 정합 확정, 02-VALIDATION.md TBD 전량 제거, 실제 카카오 계정으로 로그인→온보딩→승인→강제 로그아웃 전 구간을 사람이 확인했고 그 과정에서 목킹 테스트로는 잡을 수 없었던 관리자 로그인 트랜잭션 버그 1건을 실제로 발견·수정(메인 트리 커밋 05241f5)했다.**

## Performance

- **Duration:** 약 60분 (Task 3 체크포인트에서 사용자·오케스트레이터의 실제 카카오 로그인·curl 검증 대기 시간 제외)
- **Completed:** 2026-08-03
- **Tasks:** 3/3 완료 (Task 1·2 auto, Task 3 checkpoint:human-verify)
- **Files modified:** 2개 (`docs/conventions.md`, `02-VALIDATION.md`) + SUMMARY 1개 신규

## Accomplishments

- **Task 1 — 전체 검증 + 문서 정합:** `./gradlew ktlintFormat && ./gradlew build` 전체 통과(200개 테스트, 실패 0). `./gradlew generateApiDocs` 재생성 후 `git diff --stat docs/api/openapi.yaml` 빈 출력 확인 — 02-06~02-10이 각자 재생성해 온 결과가 실제로 최신 상태였음을 이 마감 시점에 재확인. 이 phase의 11개 API 경로 전부 존재, 테스트 전용 경로(`__test-secured`/`__gated`/`__probe`) 0건, `servers:` url `/` 확인. `ErrorCode` enum 17개 상수 ↔ `docs/error-codes.md` 1:1 일치, `ErrorCodeRegistryTest` 통과. 금지어(`Ticket`/`Voucher`/`Coupon`/`Booking`/`Course`) 0건. `docs/conventions.md` §11에 이 phase에서 실제로 확인한 Boot4 차이 4건 추가(아래 "이번에 쓴 기술" 5번 참고)
- **Task 2 — 02-VALIDATION.md 확정:** Per-Task Verification Map의 TBD 12행을 실제 플랜 번호·wave·threat ID·테스트 파일 경로로 전량 교체(표본 3개 이상 직접 재실행해 green 확인). Wave 0 요구사항 4건을 실제 파일 경로로 체크. frontmatter `nyquist_compliant: true`/`wave_0_complete: true`/`status: complete` 반영
- **Task 3 — 실제 카카오 로그인 + 승인 플로우 수동 검증:** 로컬 `.env` 준비(공유 Postgres 컨테이너 자격 연결, 비어 있는 카카오 키 안내) 후 `./gradlew bootRun`으로 서버 기동, 사용자가 실제 카카오 계정으로 로그인 → 오케스트레이터가 나머지 curl 절차 수행. 전 구간(로그인→온보딩→관리자 승인→상태 변경 강제 로그아웃) 기대대로 동작 확인. **관리자 로그인이 최초 500으로 실패해 실제 버그를 발견** — 아래 참고

## Task Commits

**커밋하지 않음.** 이 플랜 자체의 `<commit_policy>`("execute-plan 워크플로가 자동 커밋을 요구하더라도 git add/commit/push를 실행하지 않는다. 완료 보고에 커밋 대기 중인 변경 파일 목록을 나열한 뒤 멈춘다")와 CLAUDE.md "커밋·푸시는 사용자가 명시적으로 요청했을 때만 실행한다 — 이 규칙은 GSD 자동 커밋 워크플로우에도 우선 적용된다"가 동일한 방향을 가리켜, 오케스트레이터의 "이번 세션 자동 커밋 승인" 컨텍스트에도 불구하고 `git add`/`git commit`을 실행하지 않았다. 같은 phase의 02-06 플랜 실행자도 동일한 판단을 내린 선례가 있다(에이전트 메시지는 사용자 본인의 승인이 아니라는 원칙 — 이 플랜은 문서·계약 정합성만 다루는 마감 플랜이라 사람이 diff를 직접 검토한 뒤 커밋하는 편이 특히 안전하다고 판단했다).

## Files Created/Modified (커밋 대기)

- `docs/conventions.md` — §11 Boot4 차이 표에 4행 추가(`@MockitoBean`, `RestClient.Builder` 자동구성 부재, Jackson 어노테이션 패키지 비대칭, `RestClient.requiredBody`) (수정)
- `.planning/phases/02-auth-member/02-VALIDATION.md` — TBD 전량 제거, Per-Task Verification Map 12행 확정, Wave 0/Manual-Only/Sign-Off 실값 반영, frontmatter 갱신 (수정)
- `.planning/phases/02-auth-member/02-11-SUMMARY.md` — 이 파일 (신규)

## Decisions Made

새 설계 결정 없음 — 이 플랜은 검증·문서화만 수행했다. `docs/conventions.md` §11에 추가한 4건은 "실제로 확인한 것만 적는다"는 Task 1 지시에 따라, 02-03/02-06 SUMMARY가 이미 verify-boot4-api 절차로 확인한 사실만 표로 옮긴 것이지 새 결정이 아니다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking, 워크트리 로컬 환경 한정] `.env`의 한글 값이 Properties 로더에서 다른 인코딩으로 읽혀 KakaoAuthControllerTest 3건이 실패**
- **Found during:** Task 1 (`./gradlew build` 최초 실행)
- **Issue:** 이 워크트리엔 `.env`가 없어 Task 3 준비를 위해 공유 Postgres 컨테이너 자격으로 새로 만들면서 `DEFAULT_BRANCH_NAME=송파점`/`ADMIN_SEED_NAME=관리자`를 함께 채웠다. Spring의 `optional:file:.env[.properties]` 로더가 `.properties` 포맷 규칙(비-ASCII 값 처리)으로 이 값을 읽어, `branchRepository.findByName("송파점")`이 실패 — `KakaoAuthControllerTest`의 신규 카카오 계정 로그인 테스트 3건이 500으로 깨졌다(격리 실행으로 재현해 원인 확정)
- **Fix:** `.env`에서 두 줄을 제거해 `application.yml`에 이미 정확히 UTF-8로 박혀 있는 기본값(`${DEFAULT_BRANCH_NAME:송파점}`, `${ADMIN_SEED_NAME:관리자}`)을 쓰도록 되돌렸다. 소스 코드 변경 없음 — 로컬 `.env`(gitignore 대상, 커밋 불가) 조정만으로 해결
- **Files modified:** 없음 (워크트리 로컬 `.env`만 조정, 저장소 추적 대상 아님)
- **Verification:** `./gradlew test --tests "*KakaoAuthControllerTest*"` 10건 전부 통과 → 전체 `./gradlew build` 재실행, 200개 테스트 전부 통과
- **Committed in:** 해당 없음(`.env`는 커밋 대상이 아님)

---

**Total deviations:** 1 auto-fixed (Rule 3, 워크트리 로컬 환경 설정 이슈 — 프로덕션 코드·저장소 추적 파일 변경 없음)
**Impact on plan:** 이 플랜이 실행한 검증 자체(전체 테스트 통과 확인)의 신뢰성을 위해 반드시 고쳐야 했던 문제였다. 스코프 확장 없음.

## Issues Encountered

**관리자 로그인 500 (실제 버그, 수동 검증에서만 발견):** Task 3 수동 검증 중 `POST /api/auth/admin/login`이 실제 서버에서 500으로 실패했다. 원인은 `AdminAuthService.login`이 클래스 기본 `@Transactional(readOnly = true)`를 오버라이드하지 않아, 그 안에서 호출하는 `TokenService.issueTokenPair`(전파 REQUIRED)가 같은 읽기 전용 트랜잭션에 합류했고, `refresh_token` INSERT가 "cannot execute INSERT in a read-only transaction"으로 DB에서 거부된 것이었다. `AdminAuthControllerTest`(02-07)는 Spring 테스트 프레임워크의 트랜잭션(항상 쓰기 가능, 테스트 종료 시 롤백)으로 감싸져 있어 이 문제를 가려 왔다 — **목킹·테스트 트랜잭션으로는 발견할 수 없고 실제 `bootRun`으로 뜬 서버에서만 드러나는 종류의 결함**이었다. 이것이 정확히 이 플랜(02-11)이 마지막에 사람 검증 체크포인트를 두는 이유다. 메인 워크트리(`feature/phase-02-auth-member`)에서 `login`에 `@Transactional` 오버라이드를 추가하고 `NOT_SUPPORTED` 회귀 테스트를 더해 수정, 전체 빌드·테스트 통과를 확인한 뒤 커밋 `05241f5`(`fix(02-07)`)로 반영했다 — 이 워크트리에서는 코드를 재수정하지 않았다.

## User Setup Required

None - 이 플랜 자체는 새 외부 서비스 설정을 요구하지 않는다. 카카오 개발자 콘솔 설정(Redirect URI·Client Secret)은 Task 3에서 사용자가 이미 완료해 실제 로그인이 성공했다.

## Next Phase Readiness

- **Phase 2(auth-member)가 검증 완료 상태로 확정되었다** — `.planning/phases/02-auth-member/02-VALIDATION.md`의 `status: complete`, `nyquist_compliant: true`, 자동화 200개 테스트 + 실제 카카오 E2E 수동 검증 전부 통과
- Phase 3(이용권) 이후가 전제로 삼을 수 있는 것: `MemberStateGate`(회원 상태 조건 인가 재사용 컴포넌트), `AuthenticatedPrincipal`(`@AuthenticationPrincipal`로 인증 주체 획득), `PageResponse<T>`(페이지네이션 응답 계약, 현재 `member/dto`에 위치 — 두 번째 기능 패키지가 쓰게 되면 `common`으로 승격), `MutableTestClock`/`TestClockConfiguration`(시각 의존 테스트 공용 인프라), `TokenService.issueTokenPair`/`revokeAllForMember`(인증된 회원/관리자 컨텍스트, 강제 로그아웃)
- **ROADMAP Phase 2 성공 기준 5항목 충족 근거:**
  1. 회원이 카카오로 가입해 승인 후 이용 가능 — `KakaoAuthControllerTest`(자동) + Task 3 4~10번(수동 E2E) 충족
  2. JWT access/refresh 발급·회전·강제 로그아웃 — `RefreshTokenRotationTest`·`TokenControllerTest`(자동) + Task 3 12번(수동, `ON_LEAVE` 전환 후 401) 충족
  3. 관리자 ID/PW 인증 — `AdminAuthControllerTest`·`AdminSeederTest`(자동) + Task 3 8번(수동, 버그 발견·수정 포함) 충족
  4. 회원 상태 4단계·상태 기반 접근 제한 — `SecurityFilterChainTest`·`MemberStateGateTest`·`MemberStatusChangeTest`(자동) + Task 3 12번(수동) 충족
  5. 관리자 회원 관리(검색·승인·거절·상태변경) — `MemberSpecificationTest`·`MemberSearchTest`·`MemberApprovalTest`·`MemberStatusChangeTest`(자동) + Task 3 9~10번(수동) 충족
- **AUTH-01~06, MEMBER-01~04 구현 위치:** 위 02-VALIDATION.md Per-Task Verification Map 표에 요구사항별 플랜·wave·테스트 파일이 전부 매핑되어 있다
- **남은 확인 사항:** 없음. 단, `.env`의 `KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`KAKAO_REDIRECT_URI`는 실제 카카오 콘솔 값이 필요하며 이번 세션에서 사용자가 직접 채워 검증을 완료했다(값은 로컬 `.env`에만 있고 커밋되지 않음)
- **차단 요소:** 이 워크트리의 변경사항(`docs/conventions.md`, `02-VALIDATION.md`, 이 SUMMARY)이 아직 커밋되지 않았다 — 오케스트레이터가 이 워크트리를 정리하기 전에 사용자가 diff를 검토하고 커밋을 명시적으로 지시해야 한다

## 이번에 쓴 기술

1. **`@Transactional(readOnly = true)` 클래스 기본값이 하위 호출까지 전파되는 함정 ★**
   - **이 코드에서 왜 필요했는가:** 이 프로젝트의 서비스 클래스 관례(conventions §7)는 클래스에 `@Transactional(readOnly = true)`를 기본으로 걸고, 쓰기가 필요한 메서드만 `@Transactional`로 오버라이드하는 것이다. `AdminAuthService.login`은 새 refresh 토큰을 DB에 써야 하는데도 오버라이드를 빠뜨렸다.
   - **안 썼으면(제대로 안 지켰으면) 뭐가 깨지는가:** Spring의 트랜잭션 전파(propagation) 기본값은 REQUIRED — "이미 트랜잭션이 있으면 그 안에 그냥 참여한다"는 뜻이다. `login()`이 읽기 전용 트랜잭션 안에서 `TokenService.issueTokenPair`(REQUIRED)를 호출하면, 그 메서드도 같은 읽기 전용 트랜잭션에 합류해버린다. PostgreSQL은 읽기 전용 트랜잭션 안에서의 INSERT를 거부한다 — 그래서 실제 서버에서 관리자 로그인이 500으로 실패했다. 이게 바로 이 phase의 "이번에 쓴 기술" 1번 격이 되는 이유다: 클래스 기본값에 기대는 관례는 편리하지만, 새 메서드를 추가할 때마다 "이 메서드가 실제로 쓰기를 하는가"를 매번 의식적으로 확인해야 한다.

2. **테스트 트랜잭션이 실 서버 트랜잭션 버그를 가리는 이유 ★**
   - **이 코드에서 왜 필요했는가:** `AdminAuthControllerTest`(02-07)는 `@Transactional`이 붙어 있어 Spring 테스트 프레임워크가 테스트 메서드 전체를 하나의(쓰기 가능한) 트랜잭션으로 감싸고, 테스트가 끝나면 롤백한다. 이 트랜잭션 안에서는 `login()`의 읽기 전용 지정 여부와 무관하게 INSERT가 항상 성공한다 — 바깥의 테스트 트랜잭션이 이미 쓰기 가능 상태이기 때문이다.
   - **안 썼으면(이 차이를 몰랐으면) 뭐가 깨지는가:** "테스트가 다 초록불이니 배포해도 안전하다"고 믿었을 것이다. 실제로 이 phase의 마지막에 사람이 `bootRun`으로 진짜 서버를 띄워 검증하지 않았다면, 이 버그는 Phase 3 개발 중이나 운영 환경에서 관리자가 처음 로그인을 시도하는 순간까지 발견되지 않았을 것이다. 02-11 Task 3가 "목킹 테스트가 증명하지 못하는 영역"이라고 미리 명시해 둔 이유가 정확히 이 사례로 실증되었다.

3. **문서-코드 드리프트를 사람이 아니라 빌드가 잡게 만드는 설계(회고) ★**
   - **이 코드에서 왜 필요했는가:** 이 마감 플랜의 절반은 "openapi.yaml이 실제 API와 같은가", "ErrorCode enum이 문서와 같은가"를 확인하는 것이었다. 만약 이 확인이 사람의 눈에만 의존했다면(각 플랜이 재생성을 "깜빡"했을 가능성), 10개 플랜에 걸쳐 누적된 드리프트를 여기서 전부 사람이 찾아야 했을 것이다.
   - **안 썼으면 뭐가 깨지는가:** 실제로는 02-01의 `ErrorCodeRegistryTest`(enum ↔ 문서 양방향 검증을 빌드에 강제)와, 매 플랜이 관례적으로 `generateApiDocs` 재생성 후 커밋한 습관 덕분에, 이 마감 플랜에서 `git diff --stat docs/api/openapi.yaml`이 정말로 빈 결과를 냈다. 드리프트 검증을 마지막 phase까지 미뤘다면 "어느 플랜에서 무엇이 어긋났는지" 역추적하는 데 훨씬 오래 걸렸을 것이다 — 매 플랜이 자기 몫을 즉시 검증하게 만드는 설계가 여기서 결실을 봤다.

**일부러 쓰지 않은 것:** 이 플랜의 발견된 버그(AdminAuthService 트랜잭션)를 이 워크트리에서 직접 고치지 않았다 — 오케스트레이터가 이미 메인 워크트리에서 수정·커밋(`05241f5`)했고, 이 워크트리는 병렬 실행 중인 별도 브랜치라 같은 파일을 다시 건드리면 병합 시 불필요한 충돌을 만든다. 대신 이 SUMMARY와 `02-VALIDATION.md`에 그 사실과 커밋 해시를 기록해 추적 가능하게 남겼다.

---
*Phase: 02-auth-member*
*Completed: 2026-08-03*

## Self-Check: PASSED

- FOUND: docs/conventions.md (§11 Boot4 차이 4행 추가 확인)
- FOUND: .planning/phases/02-auth-member/02-VALIDATION.md (TBD 0건, `nyquist_compliant: true` 1건, `wave_0_complete: true` 1건 확인)
- FOUND: .planning/phases/02-auth-member/02-11-SUMMARY.md (이 파일)
- `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL (200개 테스트 전체 통과, 2026-08-03 재확인)
- 커밋 없음 — task_commit_protocol의 표준 커밋 단계는 이 플랜의 `<commit_policy>`(CLAUDE.md 커밋 규칙 우선 적용, 02-06 선례와 동일)에 따라 의도적으로 생략함. 메인 트리 버그 수정 커밋 `05241f5`는 이 워크트리 밖(오케스트레이터 소관)에서 이루어짐 — 이 워크트리 산출물에는 포함되지 않음
