---
phase: 02-auth-member
plan: 15
subsystem: api
tags: [kotlin, spring-boot, springdoc, openapi, member-search, admin]

# Dependency graph
requires:
  - phase: 02-auth-member (02-09/02-10/02-14)
    provides: AdminMemberController.search + MemberSearchCondition(검색·필터·페이지네이션) 원본 구현
provides:
  - "GET /api/admin/members의 openapi.yaml 계약이 keyword/status/onboardingCompleted/page/size 5개 독립 쿼리 파라미터로 기술된다(WR-06 닫힘)"
  - "조건 DTO를 @ModelAttribute로 받는 향후 목록·검색 엔드포인트가 따를 규칙(D-054)"
affects: [02-VERIFICATION, 이후 페이즈의 목록·검색 엔드포인트]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@ModelAttribute 조건 DTO에는 springdoc @ParameterObject(org.springdoc.core.annotations)를 함께 붙여 openapi.yaml이 객체 파라미터 1개가 아니라 필드별 개별 쿼리 파라미터로 생성되게 한다(D-054)"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt
    - docs/api/openapi.yaml
    - docs/decisions.md

key-decisions:
  - "D-054: 쿼리 조건 DTO는 @ParameterObject로 개별 파라미터로 펼쳐 기술한다"

patterns-established:
  - "스펙 생성 힌트(@ParameterObject)와 런타임 바인딩(@ModelAttribute)은 서로 다른 관심사이므로 항상 함께 붙인다 — 하나만 있으면 계약과 실제 동작이 갈라진다."

requirements-completed: [MEMBER-02]

# Metrics
duration: 약 5분30초(사전 조사·독해 제외, 첫 프로덕션 커밋~마지막 커밋 기준 약 1분29초 + generateApiDocs 재실행 검증 시간 포함)
completed: 2026-08-03
---

# Phase 2 Plan 15: openapi.yaml 회원 목록 쿼리 계약을 개별 파라미터로 펼침 (WR-06) 갭 클로저 Summary

**02-REVIEW.md WR-06을 닫았다 — `GET /api/admin/members`의 openapi.yaml 계약을 `condition` 객체 파라미터 1개에서 springdoc `@ParameterObject`로 `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개 독립 쿼리 파라미터로 재생성했다.**

## Performance

- **Duration:** 첫 프로덕션 커밋(11:26:32) ~ 마지막 커밋(11:28:01) 약 1분29초. `generateApiDocs` 재실행을 통한 임포트 경로 확인·멱등성 검증 시간 포함. 사전 조사·독해 시간 제외.
- **Completed:** 2026-08-03
- **Tasks:** 2/2 완료
- **Files modified:** 3 (모두 기존 파일 수정, 신규 파일 없음)

## Accomplishments

- `AdminMemberController.search`의 `condition` 파라미터에 springdoc `@ParameterObject`(패키지: `org.springdoc.core.annotations`, 로컬 Gradle 캐시의 `springdoc-openapi-starter-common-3.0.3.jar` 클래스 목록으로 실제 확인) 추가. `@ModelAttribute`·`@Valid`는 그대로 유지해 런타임 바인딩 무변경
- `./gradlew generateApiDocs`로 `docs/api/openapi.yaml` 재생성: `/api/admin/members` GET의 `parameters`가 `condition` 객체 파라미터 1개에서 `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개 독립 쿼리 파라미터로 바뀜(각각 `in: query`, `required: false`, 원래 `@field:Schema` 설명·기본값·min/max 유지). 더 이상 참조되지 않는 `components.schemas.MemberSearchCondition` 정의는 스펙에서 자동으로 사라짐
- `MemberSearchTest` 전체 통과로 서버 바인딩 동작이 바뀌지 않았음을 확인
- `docs/decisions.md`에 D-054 기록: 조건 DTO는 항상 `@ParameterObject`로 펼쳐 기술한다는 규칙을 이후 페이즈에도 적용
- `./gradlew ktlintFormat && ./gradlew build` 전체 통과
- `./gradlew generateApiDocs` 재실행으로 openapi 재생성이 멱등함(추가 드리프트 없음) 재확인

## Task Commits

Each task was committed atomically:

1. **Task 1: @ParameterObject 적용 + openapi.yaml 재생성** - `ede4446` (fix)
2. **Task 2: 결정 기록 + 포맷·전체 빌드 + openapi 드리프트 재확인** - `c390a7d` (docs)

_Note: 이 SUMMARY 커밋(및 있다면 STATE.md/ROADMAP.md 업데이트)은 오케스트레이터가 별도로 처리한다._

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` - `search`의 `condition` 파라미터에 `@ParameterObject` 추가(스펙 생성 힌트), 근거를 설명하는 주석 추가
- `docs/api/openapi.yaml` - `/api/admin/members` GET의 `parameters`를 `condition` 객체 1개→개별 파라미터 5개로 재생성, `MemberSearchCondition` 스키마 정의는 참조가 끊겨 자동 제거
- `docs/decisions.md` - D-054 추가

## Decisions Made

- **D-054 (쿼리 조건 DTO는 `@ParameterObject`로 개별 파라미터로 펼쳐 기술한다):** `@ModelAttribute`로 받는 조건 DTO 파라미터에는 항상 springdoc `@ParameterObject`를 함께 붙인다. 객체 파라미터 표현은 OpenAPI 기본 해석(`style: form, explode: true`)으로는 등가지만 `deepObject`나 JSON 문자열로 직렬화하는 생성기·클라이언트에서는 서버가 값을 전혀 바인딩하지 못하기 때문. 기각 대안: 개별 `@RequestParam` 5개로 풀기(검증·기본값 산개), 생성된 yaml 수기 수정(D-029 재생성이 되돌림), FE 직렬화 설정으로 회피(계약이 아니라 소비자 구현 의존). 상세 근거는 `docs/decisions.md` D-054 참고.

## `@ParameterObject` 임포트 경로 확인 절차 (verify-boot4-api)

CLAUDE.md 규칙 9에 따라 추측하지 않고 확인했다:

1. `find ~/.gradle/caches/modules-2/files-2.1 -iname "springdoc-openapi*.jar"`로 로컬 캐시의 springdoc 3.0.3 아티팩트 3종(starter-common/webmvc-api/webmvc-ui) 확인
2. `unzip -l springdoc-openapi-starter-common-3.0.3.jar | grep -i ParameterObject`로 클래스 실제 위치 확인 → `org/springdoc/core/annotations/ParameterObject.class`
3. 확인된 경로(`org.springdoc.core.annotations.ParameterObject`)로 임포트 후 `./gradlew generateApiDocs`·`./gradlew build`(컴파일 포함) 통과로 최종 검증

## `git diff docs/api/openapi.yaml` 요약

- **의도한 변경만 포함됨:** `/api/admin/members` GET의 `parameters` 블록이 `condition` 1개 → `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개로 바뀌었고, 각 파라미터는 원래 `MemberSearchCondition` 필드의 `description`·`enum`(status)·`default`(page/size)·`minimum`/`maximum` 값을 그대로 유지
- **`MemberSearchCondition` 스키마:** 다른 경로에서 참조하는 곳이 없어(`grep -c "MemberSearchCondition" docs/api/openapi.yaml` 결과 0) `components.schemas`에서 완전히 사라짐 — 정상 동작(의도한 결과)
- **`servers:` 유지 확인:** `url: /`로 변경 없음
- **변경 범위:** `git diff --stat`이 `docs/api/openapi.yaml` 1개 파일, `/api/admin/members` GET의 parameters 블록과 그로 인해 참조가 끊긴 `MemberSearchCondition` 스키마 삭제로 한정됨 — 다른 경로·스키마는 영향 없음

## Deviations from Plan

**1. [문서화만 — 코드 미변경] 애노테이션 근거 주석이 acceptance criteria의 grep 카운트를 처음에 초과시킴**
- **발견 시점:** Task 1 acceptance criteria 검증 중
- **원인:** `@ParameterObject`·`@ModelAttribute`를 설명하는 코드 주석에 그 애노테이션 이름을 리터럴로 반복 언급해, `grep -c "ParameterObject"`가 기대값(2: import+사용)보다 많은 3, `grep -c "@ModelAttribute"`가 기대값(1)보다 많은 2로 나옴
- **판단:** 계획서의 grep 카운트는 "애노테이션이 정확히 import+사용 한 번씩만 있고 중복 적용되지 않았다"를 확인하려는 의도다. 주석이 그 이름을 산문으로 언급해 카운트를 왜곡하는 것은 계획 의도와 무관한 우연한 충돌이라 판단해, 애노테이션 이름을 리터럴로 반복하지 않도록 주석 표현을 수정(기능·의미 변화 없음)해 카운트를 예상값(2/1)에 맞췄다
- **영향받는 파일:** `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` (주석 표현만 수정, `ede4446` 커밋에 포함)

그 외에는 계획서대로 실행했다.

## Issues Encountered

None. Postgres 컨테이너는 이전 세션에서 이미 기동·정상(healthy) 상태였기 때문에 `docker compose up -d`의 컨테이너 이름 충돌 오류를 그대로 두고 기존 컨테이너를 재사용했다(별도 조치 불필요).

## User Setup Required

None - 외부 서비스 설정 변경 없음. 다만 `generateApiDocs` 실행을 위해 메인 체크아웃의 `.env`를 워크트리로 복사해 사용했다(`.gitignore`로 추적 제외 확인, 커밋되지 않음).

## Next Phase Readiness

- 02-REVIEW.md의 WR-06이 코드·계약 재생성으로 닫혔다.
- 남은 02-REVIEW.md Warning은 WR-02(관리자 로그인 타이밍 부채널)이며, 이 플랜의 범위 밖이다.
- 이번 플랜은 새 의존성을 추가하지 않았다(`git status --short build.gradle.kts` 출력 없음 확인) — springdoc 3.0.3에 이미 포함된 애노테이션만 사용.
- `docs/api/openapi.yaml` 재생성이 멱등함을 재확인해, 이후 페이즈가 이 파일을 신뢰하고 FE 타입 생성에 사용할 수 있다.

---

## 이번에 쓴 기술

1. **스펙 생성(build-time)과 런타임 바인딩(request-time)의 분리** — `@ModelAttribute`는 스프링 MVC가 HTTP 쿼리 파라미터를 코틀린 객체로 채워 넣는(바인딩하는) 애노테이션이고, `@ParameterObject`는 springdoc이 앱을 실행해 `/v3/api-docs`를 만들 때 "이 객체를 문서에 어떻게 펼쳐 적을지" 알려주는 힌트다. 이번 코드에서 왜 필요했는가: 이 둘을 서로 다른 계층으로 인식하지 못하면 "바인딩이 되니까 문서도 맞겠지"라고 착각하기 쉽다. 실제로는 `@ModelAttribute`만 있으면 바인딩은 정상 동작하는데 **문서(openapi.yaml)만** `condition` 객체 파라미터 1개로 잘못 기술된다 — 서버 코드와 계약 문서가 서로 다른 애노테이션의 책임이라는 걸 몰랐다면 "테스트가 통과하는데 왜 FE가 검색이 안 된다고 하지?"라는 원인 불명 버그로 이어졌을 것이다. 안 썼으면 애초에 이 플랜의 문제(WR-06)가 발생한 그 상태 그대로 남는다.

2. **OpenAPI 쿼리 파라미터 직렬화 스타일(★, form/explode/deepObject)** — OpenAPI 스펙은 객체를 쿼리 문자열로 표현하는 방식이 여러 개다. 기본값(`style: form, explode: true`)은 `condition` 객체의 각 필드를 `keyword=abc&page=0`처럼 최상위 파라미터인 것처럼 펼쳐 보내는 방식이라, 우연히 우리 서버가 기대하는 형태와 같다. 하지만 `deepObject` 스타일은 `condition[keyword]=abc&condition[page]=0`처럼 파라미터 이름 자체에 객체 구조를 남기고, 어떤 클라이언트 생성기는 아예 JSON 문자열(`condition={"keyword":"abc"}`)로 직렬화한다. 이번 코드에서 왜 문제였는가: `docs/api/openapi.yaml`이 `condition`을 객체 스키마 하나로만 기술하고 있어서, 이 파일로 타입을 생성하는 FE 도구가 기본 해석과 다른 스타일을 고르면 서버가 그 요청을 전혀 이해하지 못한다(파라미터가 조용히 무시되고 항상 기본값 1페이지 전체가 나감). 안 썼으면(스타일 차이를 몰랐다면) "명세상으로는 맞는데 왜 실제로 필터가 안 먹지"라는, 계약 문서만 보고는 원인을 찾을 수 없는 통합 버그가 됐을 것이다.

3. **계약 문서가 코드에서 생성(generate)되어야 하는 이유** — `docs/api/openapi.yaml`을 사람이 직접 쓰지 않고 `./gradlew generateApiDocs`(D-029)로 실행 중인 서버의 `/v3/api-docs`를 받아오는 이유는, 계약과 실제 서버 동작이 **같은 소스(코드)에서 나오도록 강제**하기 위해서다. 이번 작업에서 왜 중요했는가: 만약 `condition` → 개별 파라미터로 바뀐 걸 openapi.yaml에서 손으로만 고쳤다면, 다음에 누군가 `MemberSearchCondition`에 필드를 추가하고 재생성을 깜빡하는 순간 계약이 다시 코드와 어긋난다. 이번 Task 2에서 재생성을 한 번 더 실행해 "재생성해도 추가 diff가 없다(멱등)"를 확인한 것도 같은 이유 — 재생성이 매번 다른 결과를 낸다면 애초에 코드가 유일한 진실 소스라는 전제가 무너진다. 안 썼으면(수기 편집을 허용했으면) openapi.yaml이 시간이 지날수록 실제 서버 동작과 조용히 벌어지는(drift) 문서가 되어, FE가 이 파일로 생성한 타입이 어느 시점부터 거짓말을 하게 된다.

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt
- FOUND: docs/api/openapi.yaml
- FOUND: docs/decisions.md (D-054)
- FOUND commit ede4446
- FOUND commit c390a7d

---
*Phase: 02-auth-member*
*Plan: 15*
*Completed: 2026-08-03*
