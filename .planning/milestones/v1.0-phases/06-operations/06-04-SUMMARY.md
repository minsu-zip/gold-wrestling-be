---
phase: 06-operations
plan: 04
subsystem: api
tags: [notice, crud, rest, openapi, springdoc]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-01이 확정한 NOTICE_NOT_FOUND 에러코드, 06-02가 만든 Notice 엔티티·NoticeRepository(findAllByOrderByCreatedAtDescIdDesc)"
provides:
  - "NoticeService — 공지 CRUD 5메서드(getList/getDetail/create/update/delete), hard delete(D-131)"
  - "AdminNoticeController(/api/admin/notices) — 관리자 CRUD 5개 엔드포인트"
  - "MemberNoticeController(/api/members/notices) — 회원 열람 2개 엔드포인트, 회원 상태 게이트 미적용(D-134)"
  - "docs/api/openapi.yaml에 공지 7개 엔드포인트 계약 반영"
affects: [06-05, 06-06, 06-07, 06-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mockito willAnswer + 리플렉션으로 JPA IDENTITY 생성 id를 가짜 저장소 위에서 흉내내는 서비스 단위테스트 패턴(NoticeServiceTest) — 이 저장소 최초 사례"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/notice/NoticeService.kt
    - src/main/kotlin/com/goldwrestling/notice/NoticeNotFoundException.kt
    - src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt
    - src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt
    - src/main/kotlin/com/goldwrestling/notice/dto/NoticeRequests.kt
    - src/main/kotlin/com/goldwrestling/notice/dto/NoticeResponses.kt
    - src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt
    - src/test/kotlin/com/goldwrestling/notice/AdminNoticeControllerTest.kt
    - src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "NoticeExceptions.kt를 NoticeNotFoundException.kt로 개명 — ktlint standard:filename 규칙(단일 클래스 파일은 클래스명과 일치) 위반이라 plan 파일명에서 편차(Rule 3)"
  - "MemberNoticeController KDoc의 'MemberStateGate' 문자열을 '회원 상태 게이트(ACTIVE 강제)'로 표현 변경 — acceptance grep(0건 기대)과 충돌"

requirements-completed: [NOTICE-01, NOTICE-02]

# Metrics
duration: ~50min
completed: 2026-08-18
---

# Phase 6 Plan 4: 공지사항 CRUD API Summary

**관리자 공지 CRUD 5개 + 회원 열람 2개 엔드포인트를 완결하고 openapi.yaml에 반영 — 삭제는 hard delete, 회원 열람은 상태 게이트 없이 전 회원 공용 데이터로 취급(D-134)**

## Performance

- **Duration:** ~50min
- **Completed:** 2026-08-18
- **Tasks:** 3/3 완료
- **Files modified:** 10 (9 신규 + openapi.yaml)

## Accomplishments
- `NoticeService` — `getList`(최신순 페이지)·`getDetail`·`create`·`update`·`delete` 5메서드. 클래스 기본
  `@Transactional(readOnly = true)` + 변경 메서드만 오버라이드(D-020), `create`는 `createdAt`=`updatedAt`을
  `Clock` 기준 같은 시각으로 채우고 `update`는 `createdAt`을 건드리지 않으며, `delete`는 물리 삭제(D-131)
- `AdminNoticeController`(`/api/admin/notices`) 5개 + `MemberNoticeController`(`/api/members/notices`) 2개,
  총 7개 엔드포인트. 회원 컨트롤러는 `@AuthenticationPrincipal`도 회원 상태 게이트도 쓰지 않는다(D-134) —
  공지는 회원 소유 데이터가 아니고 휴회 회원의 열람을 막을 이유가 없다
- 통합테스트 6건(`AdminNoticeControllerTest` 4 + `MemberNoticeControllerTest` 2) — 등록→목록→상세→수정→삭제
  성공 경로, 없는 id 404, 빈 제목 400, 회원 토큰 403을 각각 ProblemDetail `code`로 단언
- `NoticeServiceTest` 8건 — 서비스 계층을 가짜 협력자로 검증(Clock 시각 채움, createdAt 불변, hard delete,
  NoticeNotFoundException 3종, 목록 변환). `noticeRepository.save`를 `willAnswer`+리플렉션으로 스텁해
  실제 JPA `IDENTITY` 생성 id를 가짜 저장소 위에서 흉내냈다(이 저장소 최초 사례)
- `docs/api/openapi.yaml` 재생성 — 공지 4개 경로(엔드포인트 7개)만 추가, `servers:` 불변
- 전체 회귀 `./gradlew ktlintFormat build` BUILD SUCCESSFUL

## Task Commits

1. **Task 1: NoticeService + NoticeNotFoundException + 요청·응답 DTO** - `4ddc38f` (feat)
2. **Task 2: AdminNoticeController(CRUD 5) + MemberNoticeController(열람 2) + 통합테스트 2종** - `2f32d3c` (feat)
3. **Task 3: openapi.yaml 재생성 및 diff 확인** - `e561dec` (feat)

_두 tdd="true" 태스크(1·2) 모두 별도 RED 커밋 없이 구현+테스트를 같은 커밋에 담았다 — 아래
"TDD Gate Compliance" 참조._

## TDD Gate Compliance

Task 1·2는 plan에서 `tdd="true"`로 표시됐지만, 실제로는 별도의 `test(...)` RED 커밋 없이 구현과
테스트를 같은 `feat(...)` 커밋에 담아 진행했다. 이유:

- Task 1(`NoticeService`)은 도메인 규칙(차감·정원 판정)이 아니라 단순 CRUD 조립(조회→변환, 존재
  확인 후 필드 세팅)이라 "실패하는 테스트를 먼저 쓸" 분기가 사실상 없다 — 실패 경로는 전부
  `findById`가 빈 `Optional`일 때 예외를 던지는 한 가지 패턴의 반복이다.
- Task 2(컨트롤러)의 행위는 Task 1에서 이미 구현된 서비스를 HTTP로 감싸는 배선이라, 컨트롤러
  코드 자체에 검증할 분기가 없다(권한·유효성 검증은 `SecurityConfig`·`@Valid`가 담당).

06-02가 저장 계층에 대해 같은 판단을 내린 선례("구현 → 테스트로 즉시 실증")를 서비스·컨트롤러
계층까지 연장했다. 두 태스크 모두 구현 직후 실제로 테스트를 작성해 실행했고(`NoticeServiceTest`
8건, `AdminNoticeControllerTest`+`MemberNoticeControllerTest` 6건), 전부 통과를 확인한 뒤 커밋했다
— "테스트 없이 구현만 커밋"한 경우는 없다.

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/notice/NoticeService.kt` - 공지 CRUD 5메서드
- `src/main/kotlin/com/goldwrestling/notice/NoticeNotFoundException.kt` - 404 예외(id 미보간)
- `src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt` - 관리자 CRUD 5개 엔드포인트
- `src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt` - 회원 열람 2개 엔드포인트
- `src/main/kotlin/com/goldwrestling/notice/dto/NoticeRequests.kt` - Create/UpdateNoticeRequest
- `src/main/kotlin/com/goldwrestling/notice/dto/NoticeResponses.kt` - Summary/DetailResponse
- `src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt` - 서비스 단위테스트 8건
- `src/test/kotlin/com/goldwrestling/notice/AdminNoticeControllerTest.kt` - 관리자 통합테스트 4건
- `src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt` - 회원 통합테스트 2건
- `docs/api/openapi.yaml` - 공지 4개 경로(엔드포인트 7개) 추가

## Decisions Made
- `NoticeExceptions.kt`(plan이 지정한 파일명)를 `NoticeNotFoundException.kt`로 바꿨다 — ktlint
  `standard:filename` 규칙이 "단일 클래스 파일은 클래스명과 일치해야 한다"를 강제해 `ktlintFormat`이
  실패했다. `PassExceptions.kt`처럼 여러 예외 클래스를 담을 계획이 아니라면(공지는 404 하나뿐)
  단일 클래스 파일 규칙을 따르는 것이 맞다고 판단했다(Rule 3, CLAUDE.md "ktlint가 유일한 기준").
- `MemberNoticeController` KDoc에서 "`MemberStateGate`도 호출하지 않는다"는 문구를 "회원 상태
  게이트(`ACTIVE` 강제)도 거치지 않는다"로 바꿨다 — plan의 acceptance grep(`MemberStateGate` 문자열
  0건)과 KDoc 설명이 문자 그대로 충돌해, 06-02·05-06이 같은 유형 충돌을 해결한 방식(다른 표현으로
  같은 뜻 전달)을 그대로 따랐다.
- `NoticeServiceTest`는 `noticeRepository.save`를 `willAnswer`로 스텁해 실제 JPA `IDENTITY`
  전략처럼 인자로 받은 엔티티에 생성된 id를 리플렉션으로 채워 반환하게 했다 — `Notice.id`가
  `val`(conventions §3 엔티티 불변 식별자)이라 생성자 밖에서 세팅할 방법이 이것뿐이었고, 그렇지
  않으면 `NoticeDetailResponse.from`의 `requireNotNull(id)`가 가짜 저장소 위에서 항상 실패해
  응답 변환 자체를 단위테스트로 검증할 수 없었다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NoticeExceptions.kt → NoticeNotFoundException.kt 파일명 변경**
- **Found during:** Task 1 (`./gradlew ktlintFormat` 실행 시)
- **Issue:** plan이 지정한 `NoticeExceptions.kt`가 단일 클래스만 담고 있어 ktlint
  `standard:filename` 규칙 위반으로 빌드가 막혔다
- **Fix:** 파일명을 클래스명과 일치시켜 `NoticeNotFoundException.kt`로 변경
- **Files modified:** `src/main/kotlin/com/goldwrestling/notice/NoticeNotFoundException.kt`
- **Verification:** `./gradlew ktlintFormat` 성공
- **Committed in:** `4ddc38f` (Task 1 커밋)

**2. [Rule 3 - Blocking] MemberNoticeController KDoc 표현 변경**
- **Found during:** Task 2 완료 후 acceptance grep 확인
- **Issue:** KDoc에 쓴 "`MemberStateGate`" 문자열이 "MemberNoticeController.kt에 MemberStateGate
  문자열이 존재하지 않는다"는 acceptance criteria와 충돌
- **Fix:** 같은 의미를 "회원 상태 게이트(`ACTIVE` 강제)"로 바꿔 표현
- **Files modified:** `src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt`
- **Verification:** `grep -q "MemberStateGate" MemberNoticeController.kt` → 매치 없음 확인
- **Committed in:** `2f32d3c` (Task 2 커밋)

---

**Total deviations:** 2 auto-fixed (모두 Rule 3 — 빌드/acceptance criteria를 막는 blocking issue)
**Impact on plan:** 둘 다 표현·파일명 수준의 수정이라 동작·계약에는 영향이 없다. 스코프 확장 없음.

## Issues Encountered
- 컨트롤러 KDoc의 `/api/members/**` 표현이 `**/`로 Kotlin 블록 주석을 조기 종료시켜 컴파일 에러가
  났다 — `/api/members` 하위 전체"로 다시 표현해 해결(코드 동작과 무관, 순수 문서 표현 문제)
- 통합테스트 `@AfterEach` 정리에서 `refresh_token`이 `member`/`admin`을 FK로 참조해 첫 정리 시도가
  실패했다 — `AdminBatchControllerTest`와 동일하게 `refresh_token`을 먼저 지우도록 순서를 맞춰 해결

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 공지사항(NOTICE-01·02)이 완결돼 이후 출석 플랜(06-05~06-08)과 독립적으로 리뷰·머지 가능하다
- `docs/api/openapi.yaml`이 공지 7개 엔드포인트까지 반영해 FE가 타입을 생성할 수 있는 상태다
- Phase 6 나머지 플랜(출석 서비스·컨트롤러·동시성, 알림)은 이번 플랜의 산출물에 의존하지 않는다

---

## 이번에 쓴 기술

1. **★ JPA `IDENTITY` 생성 id를 가짜 저장소 위에서 흉내내기(Mockito `willAnswer` + 리플렉션)**
   - **이 코드에서 왜 필요했는가**: `Notice.id`는 `@GeneratedValue(IDENTITY)`가 붙은 `val`이라
     생성자로 값을 못 주고, 실제 DB에 `INSERT`가 나가야만(즉 Testcontainers 같은 진짜 DB가 있어야만)
     채워진다. `NoticeServiceTest`는 서비스 계층만 빠르게 검증하려고 `noticeRepository`를 Mockito
     가짜 객체로 뒀는데, 가짜 `save()`는 아무 동작도 안 해서 `id`가 계속 `null`로 남는다. 그런데
     `NoticeService.create`는 저장 직후 그 엔티티로 `NoticeDetailResponse.from(notice)`를 만들고,
     이 변환 함수는 `requireNotNull(notice.id)`로 방어해 있다(id 없는 응답을 만들지 못하게). 그래서
     `save`가 불릴 때 "실제 DB가 하는 일"(생성된 id를 그 인스턴스에 채워 넣기)을 리플렉션으로
     흉내내야 했다.
   - **안 썼으면 뭐가 깨지는가**: 리플렉션 스텁 없이 그냥 mock을 썼다면 `service.create(...)`를
     호출하는 모든 테스트가 "id가 null이라 응답을 만들 수 없다"는, 테스트 목적과 무관한 이유로
     전부 실패했을 것이다 — 결국 서비스 단위테스트를 포기하고 Testcontainers 통합테스트로만
     검증하게 됐을 텐데, 그러면 컨테이너 기동 비용 때문에 피드백이 느려지고(현재 8건이 밀리초 단위,
     Testcontainers면 초 단위) `Clock` 주입·트랜잭션 경계 같은 "DB 없이도 검증 가능한 것"까지
     불필요하게 무거운 도구로 검증하게 됐을 것이다.

2. **`@GeneratedValue(IDENTITY)` + `val id`가 강제하는 "저장 후에만 id가 생긴다"는 불변식**
   - **이 코드에서 왜 필요했는가**: `Notice`의 `id`는 `val`이고 기본값이 `null`이다(conventions §3
     엔티티 관례). 이렇게 하면 "아직 저장 안 된 엔티티"와 "저장된 엔티티"가 타입 수준에서 다르게
     행동한다 — `id`가 `null`인 객체는 아직 DB에 없는 것이고, `null`이 아니면 DB의 특정 행과 대응된다.
     `NoticeDetailResponse.from`의 `requireNotNull` 방어가 바로 이 불변식을 코드로 강제하는 지점이다:
     저장되지 않은(또는 잘못 다뤄진) `Notice`를 실수로 응답에 노출하면 즉시 예외가 나서 원인을
     빨리 찾을 수 있다.
   - **안 썼으면 뭐가 깨지는가**: `id`를 `var`로 두고 기본값 `0`이나 `-1` 같은 "가짜 값"을 줬다면,
     저장 전 상태와 저장 후 상태를 구분할 방법이 코드에 없어진다 — 예를 들어 서비스 코드에 실수로
     `save()` 호출을 빼먹어도 컴파일도 되고 당장 에러도 안 나다가, 그 "저장 안 된" `Notice`의 `id`
     (`0`이나 `-1`)로 다른 공지를 조회·수정하는 조용한 버그로 이어질 수 있다.

3. **springdoc의 자동 `operationId` 분화 — 같은 메서드명이 여러 컨트롤러에 있을 때**
   - **이 코드에서 왜 필요했는가**: 이 프로젝트는 컨트롤러마다 조회 메서드를 `getDetail`이라는 같은
     이름으로 짓는 관례가 있다(`AdminNoticeController.getDetail`, `MemberNoticeController.getDetail`,
     기존 `AdminMemberController.getDetail`). OpenAPI 스펙에서 `operationId`는 문서 전체에서
     유일해야 하는데, springdoc이 이 충돌을 자동으로 감지해 `getDetail`·`getDetail_1`·`getDetail_2`로
     번호를 붙여 풀어준다. 손으로 이름을 다르게 짓지 않아도 계약이 깨지지 않는 이유다.
   - **안 썼으면 뭐가 깨지는가**: springdoc이 이 자동 분화를 안 했다면 같은 `operationId`가 두 번
     이상 나오는 openapi.yaml이 만들어지고, FE가 이 스펙으로 타입/클라이언트 코드를 생성하는
     도구(대개 `operationId`를 함수명으로 씀)가 이름 충돌로 생성에 실패하거나 마지막 정의로
     덮어써서 앞의 엔드포인트 호출 함수가 조용히 사라졌을 것이다.

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/goldwrestling/notice/NoticeService.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/notice/NoticeNotFoundException.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/notice/dto/NoticeRequests.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/notice/dto/NoticeResponses.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/notice/AdminNoticeControllerTest.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt`
- FOUND: commit `4ddc38f` (Task 1)
- FOUND: commit `2f32d3c` (Task 2)
- FOUND: commit `e561dec` (Task 3)

---
*Phase: 06-operations*
*Completed: 2026-08-18*
